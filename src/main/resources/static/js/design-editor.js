(() => {
  document.querySelectorAll('.design-remove').forEach(button => button.addEventListener('click', async () => {
    const card=button.closest('[data-design-id]'); if (!confirm('Удалить дизайн «'+card.dataset.designTitle+'»?')) return;
    button.disabled=true; const status=document.getElementById('design-list-status');
    try { const header=document.querySelector('meta[name=csrf-header]').content, token=document.querySelector('meta[name=csrf-token]').content; const r=await fetch('/api/designs/'+encodeURIComponent(card.dataset.designId),{method:'DELETE',headers:{[header]:token}}); if(!r.ok) throw new Error(r.status===409?'Дизайн используется в корзине. Сначала удалите его из корзины.':'Не удалось удалить дизайн. Проверьте доступ и попробуйте снова.'); card.remove(); status.textContent='Дизайн удалён.'; if(!document.querySelector('.saved-design')) location.reload(); } catch(e){status.textContent=e.message;button.disabled=false;}
  }));
  const root = document.getElementById('editor'); if (!root || !window.Konva) return;
  const $ = id => document.getElementById(id), auth = !!$('authenticated-editor');
  const W=500,H=620, bounds={x:140,y:155,width:220,height:330};
  const state={front:[],back:[]}; let side='front', selected=null, dirty=false, saving=false, restoring=!!root.dataset.designId, loadFailed=false, revision=0, designId=root.dataset.designId || '', nextId=1;
  const stage=new Konva.Stage({container:'stage-container',width:W,height:H});
  const backdrop=new Konva.Layer(), layer=new Konva.Layer();stage.add(backdrop,layer);
  // Иллюстрация одежды не участвует в editor_state: координаты печати остаются прежними.
  const isHoodie=root.dataset.category==='HOODIE', isSweat=root.dataset.category==='SWEATSHIRT';
  const garment=new Konva.Path({data: isHoodie||isSweat
    ? 'M 194 91 Q 180 96 159 103 L 101 126 Q 87 132 79 151 L 38 350 Q 52 366 86 370 L 126 240 L 132 492 Q 250 507 368 492 L 374 240 L 414 370 Q 448 366 462 350 L 421 151 Q 413 132 399 126 L 341 103 Q 320 96 306 91 Q 250 111 194 91 Z'
    : 'M 195 91 Q 177 94 157 104 L 72 147 Q 57 155 48 171 L 91 251 Q 107 252 132 235 L 132 492 Q 250 505 368 492 L 368 235 Q 393 252 409 251 L 452 171 Q 443 155 428 147 L 343 104 Q 323 94 305 91 Q 250 112 195 91 Z',
    strokeWidth:1.5,shadowColor:'#171717',shadowBlur:18,shadowOffsetY:10,shadowOpacity:.13});
  backdrop.add(garment);
  const details=new Konva.Group({listening:false}); backdrop.add(details);
  function updateGarment(){
    const palettes={Black:['#282a29','#444643','#303231','#747771'],White:['#d9dcd5','#fffefa','#e8eae4','#a9aea5'],Grey:['#92958f','#c9cbc5','#aaada6','#777c75'],Beige:['#b8a58e','#e7d9c5','#d0bea6','#a4927e']};
    const color=$('variant').selectedOptions[0]?.dataset.color, p=palettes[color]||palettes.White;
    garment.stroke(p[3]); garment.fillLinearGradientStartPoint({x:60,y:100}); garment.fillLinearGradientEndPoint({x:430,y:480}); garment.fillLinearGradientColorStops([0,p[0],.34,p[1],.72,p[2],1,p[0]]);
    details.destroyChildren();
    const line=(data,opacity=.35,width=1.5)=>details.add(new Konva.Path({data,stroke:p[3],strokeWidth:width,opacity,lineCap:'round',lineJoin:'round'}));
    // Col, coutures et plis évoquent le tissu sans gêner la zone de création.
    if(isHoodie){
      details.add(new Konva.Path({data:side==='front'?'M 194 91 Q 194 33 250 28 Q 306 33 306 91 Q 293 117 250 126 Q 207 117 194 91 Z':'M 194 91 Q 194 30 250 27 Q 306 30 306 91 Q 285 111 250 115 Q 215 111 194 91 Z',fill:p[2],stroke:p[3],strokeWidth:1.5}));
      line(side==='front'?'M 203 88 Q 250 115 297 88':'M 203 91 Q 250 102 297 91',.7,2);
      if(side==='front'){line('M 229 111 L 225 151 M 271 111 L 275 151',.65,2);line('M 160 448 Q 250 457 340 448 M 160 448 L 157 477 Q 250 483 343 477 L 340 448',.45);}
    }else{
      details.add(new Konva.Path({data:side==='front'?'M 195 91 Q 208 128 250 129 Q 292 128 305 91 Q 293 88 286 87 Q 278 107 250 108 Q 222 107 214 87 Z':'M 195 91 Q 213 109 250 109 Q 287 109 305 91 L 286 87 Q 250 98 214 87 Z',fill:p[0],stroke:p[3],strokeWidth:1}));
      line(side==='front'?'M 202 94 Q 218 122 250 122 Q 282 122 298 94':'M 202 94 Q 250 106 298 94',.55);
    }
    line('M 132 235 Q 139 199 134 174 M 368 235 Q 361 199 366 174',.38);
    line(isHoodie||isSweat?'M 43 338 Q 64 351 89 352 M 411 352 Q 436 351 457 338':'M 85 239 Q 106 237 126 223 M 374 223 Q 394 237 415 239',.5);
    line('M 135 477 Q 250 490 365 477',.45); line('M 145 485 Q 250 496 355 485',.22);
    line('M 145 252 Q 156 290 145 325 M 355 252 Q 344 290 355 325 M 153 399 Q 158 430 149 457 M 347 399 Q 342 430 351 457',.18,2);
    if(isHoodie||isSweat) line('M 135 465 Q 250 476 365 465 M 43 340 L 40 351 M 457 340 L 460 351',.35);
    backdrop.batchDraw();
  }

  // Для белой футболки доступны отдельные изображения переда и спины.
  const garmentPhotos={};
  for (const view of ['front','back']) {
    const node=new Konva.Image({x:-30,y:108,width:560,height:373,listening:false,visible:false});
    backdrop.add(node); node.moveToBottom();
    const asset=new Image(); garmentPhotos[view]={node,ready:false};
    asset.onload=()=>{node.image(asset);garmentPhotos[view].ready=true;updateGarment();};
    asset.src=`/images/editor/tshirt-${view}-white.png`;
  }
  const drawIllustration=updateGarment;
  updateGarment=function(){
    drawIllustration();
    const color=$('variant').selectedOptions[0]?.dataset.color;
    const usePhoto=garmentPhotos[side].ready&&!isHoodie&&!isSweat&&(!color||color==='White');
    for (const view of ['front','back']) garmentPhotos[view].node.visible(usePhoto&&view===side);
    garment.visible(!usePhoto);details.visible(!usePhoto);
    backdrop.batchDraw();
  };
  const printBoundary = new Konva.Rect({...bounds,stroke:'#737b69',strokeWidth:1,dash:[7,6],listening:false});
  backdrop.add(printBoundary);
  const transformer=new Konva.Transformer({rotateEnabled:true,keepRatio:true,flipEnabled:false,enabledAnchors:['top-left','top-right','bottom-left','bottom-right'],borderStroke:'#485200',anchorStroke:'#485200',anchorFill:'#d9ff43',anchorSize:9});layer.add(transformer);
  const objectUrls=new Map(), images=new Map();
  let previewMode=false, history=[], historyIndex=-1, historyBusy=false, pendingAssets=0, lastValid=null;
  const status=(message,error=false)=>{if($('save-status')){$('save-status').textContent=message;$('save-status').classList.toggle('editor-error',error)}};
  const csrf=()=>({[document.querySelector('meta[name=csrf-header]').content]:document.querySelector('meta[name=csrf-token]').content});
  const uuid=()=>crypto.randomUUID();
  const imageFromUrl=url=>new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=()=>reject(new Error('Не удалось открыть изображение.'));image.src=url;});
  const all=()=>[...state.front,...state.back];
  function serialize(item,internal=false){
    const n=item.node, out={type:item.type,id:item.id,name:item.name,visible:item.visible,x:n.x(),y:n.y(),scaleX:n.scaleX(),scaleY:n.scaleY(),rotation:n.rotation()};
    if(item.type==='text')Object.assign(out,{autoWidth:item.autoWidth,width:n.width(),height:n.height(),text:n.text(),fontSize:n.fontSize(),color:n.fill(),fontFamily:n.fontFamily(),fontStyle:n.fontStyle(),align:n.align(),letterSpacing:n.letterSpacing()});
    if(item.type==='image'){out.width=n.width();out.height=n.height();out.fileId=item.fileId;if(internal&&item.localKey)out.localKey=item.localKey;}
    if(item.type==='preset')out.presetId=item.presetId;
    if(item.type==='shape')Object.assign(out,{shapeId:item.shapeId,color:n.fill()});
    return out;
  }
  function historySnapshot(){return {front:state.front.map(i=>serialize(i,true)),back:state.back.map(i=>serialize(i,true))};}
  function updateHistoryButtons(){if($('undo-action'))$('undo-action').disabled=previewMode||historyBusy||pendingAssets>0||historyIndex<=0;if($('redo-action'))$('redo-action').disabled=previewMode||historyBusy||pendingAssets>0||historyIndex>=history.length-1;}
  function pendingChange(delta){pendingAssets+=delta;updateHistoryButtons();if($('save-design'))$('save-design').disabled=pendingAssets>0||saving||restoring||loadFailed;updateCart();}
  function checkpoint(){history=history.slice(0,historyIndex+1);history.push(historySnapshot());if(history.length>80)history.shift();historyIndex=history.length-1;updateHistoryButtons();}
  function changed(record=true){revision++;dirty=true;status('Есть несохранённые изменения');updatePrice();updateCart();if(record)checkpoint();}
  function resize(){const w=$('stage-container').clientWidth;if(!w)return;const scale=w/W;stage.width(w);stage.height(H*scale);stage.scale({x:scale,y:scale});stage.draw();}
  new ResizeObserver(resize).observe($('stage-container'));resize();
  function updatePrice(){const option=$('variant').selectedOptions[0],price=Number(option?.dataset.price);$('total-price').textContent=option?.value ? `${(price+(all().length?1000:0)).toLocaleString('ru-RU')} ₸`:'—';$('variant-info').textContent=option?.value?`Доступно: ${option.dataset.stock} шт.`:'Выберите доступный вариант.';}
  function updateCart(){const form=$('add-to-cart');if(!form)return;form.querySelector('[name=variantId]').value=$('variant').value;form.querySelector('[name=designId]').value=designId;form.querySelector('button').disabled=!designId||dirty||!$('variant').value||saving||restoring||loadFailed||historyBusy||pendingAssets>0;}
  function rect(item){return item.node.getClientRect({relativeTo:layer,skipShadow:true,skipStroke:true});}
  function inside(item){const r=rect(item),e=.75;return r.x>=bounds.x-e&&r.y>=bounds.y-e&&r.x+r.width<=bounds.x+bounds.width+e&&r.y+r.height<=bounds.y+bounds.height+e;}
  function warning(){if($('bounds-warning'))$('bounds-warning').hidden=!state[side].some(item=>item.visible&&!inside(item));}
  function positionInBounds(item){const r=rect(item), n=item.node;let dx=0,dy=0;if(r.x<bounds.x)dx=bounds.x-r.x;else if(r.x+r.width>bounds.x+bounds.width)dx=bounds.x+bounds.width-r.x-r.width;if(r.y<bounds.y)dy=bounds.y-r.y;else if(r.y+r.height>bounds.y+bounds.height)dy=bounds.y+bounds.height-r.y-r.height;n.x(n.x()+dx);n.y(n.y()+dy);}
  function geometry(n){return {x:n.x(),y:n.y(),scaleX:n.scaleX(),scaleY:n.scaleY(),rotation:n.rotation()};}
  function restoreGeometry(n,g){if(g)n.setAttrs(g);}
  function controls(){const active=!!selected&&!previewMode;$('element-controls').disabled=!active;const n=selected?.node,isText=selected?.type==='text',isShape=selected?.type==='shape';['text-content','font-size','font-family','font-weight','text-align','letter-spacing'].forEach(id=>{if($(id))$(id).disabled=!isText||!active});if($('text-color'))$('text-color').disabled=(!isText&&!isShape)||!active;if(n){$('position-x').value=Math.round(n.x());$('position-y').value=Math.round(n.y());$('scale').value=Math.round(n.scaleX()*100);$('rotation').value=Math.round(n.rotation());if(isText){$('text-content').value=n.text();$('font-size').value=n.fontSize();$('text-color').value=n.fill();$('font-family').value=n.fontFamily();$('font-weight').value=n.fontStyle();$('text-align').value=n.align();$('letter-spacing').value=n.letterSpacing();}else if(isShape)$('text-color').value=n.fill();}}
  function tool(name){document.querySelectorAll('[data-panel]').forEach(p=>p.hidden=p.dataset.panel!==name);document.querySelectorAll('[data-tool]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tool===name)));}
  document.querySelectorAll('[data-tool]').forEach(b=>b.addEventListener('click',()=>tool(b.dataset.tool)));
  function list(){
    const ul=$('element-list');ul.replaceChildren();$('empty-elements').hidden=state[side].length>0;
    [...state[side]].reverse().forEach(item=>{
      const li=document.createElement('li');li.className='layer-item';
      const pick=document.createElement('button');pick.type='button';pick.textContent=item.name;pick.setAttribute('aria-pressed',String(item===selected));pick.addEventListener('click',()=>select(item));li.append(pick);
      const action=(label,text,fn)=>{const b=document.createElement('button');b.type='button';b.textContent=text;b.setAttribute('aria-label',label+' «'+item.name+'»');b.title=label;b.addEventListener('click',fn);li.append(b);};
      action(item.visible?'Скрыть':'Показать',item.visible?'◉':'○',()=>{item.visible=!item.visible;item.node.visible(item.visible);if(!item.visible&&selected===item)select(null);list();layer.batchDraw();changed();});
      action('Переименовать','✎',()=>{const name=prompt('Название слоя:',item.name);if(name===null)return;const value=name.trim().slice(0,100);if(!value)return;item.name=value;list();changed();});
      action('Выше','↑',()=>move(item,1));action('Ниже','↓',()=>move(item,-1));action('Удалить','×',()=>remove(item));ul.append(li);
    });
  }
  function select(item){if(previewMode)item=null;selected=item;transformer.nodes(item&&item.visible?[item.node]:[]);transformer.moveToTop();controls();list();layer.batchDraw();}
  function bind(item){const n=item.node;n.on('click tap',()=>{if(!previewMode)select(item)});n.on('dragstart transformstart',()=>{lastValid=geometry(n)});n.on('dragmove',()=>{positionInBounds(item);controls();warning();});n.on('transform',()=>{if(!inside(item)){restoreGeometry(n,lastValid);transformer.forceUpdate()}else lastValid=geometry(n);controls();warning();});n.on('dragend transformend',()=>{if(!inside(item))restoreGeometry(n,lastValid);controls();warning();changed();});}
  function addItem(type,node,data={},where=side){const item={type,node,id:data.id||uuid(),name:data.name||({text:'Текст',image:'Изображение',preset:'Принт',shape:'Фигура'}[type]),visible:data.visible!==false,autoWidth:data.autoWidth===true,fileId:data.fileId||null,localKey:data.localKey||null,presetId:data.presetId,shapeId:data.shapeId};state[where].push(item);node.visible(item.visible);bind(item);if(where===side){layer.add(node);select(item);}return item;}
  function drawSide(){updateGarment();layer.getChildren().filter(n=>n!==transformer).forEach(n=>n.remove());for(const item of state[side]){item.node.visible(item.visible);item.node.draggable(!previewMode);layer.add(item.node);}printBoundary.visible(!previewMode);select(null);warning();layer.draw();}
  function remove(item){if(!item)return;const i=state[side].indexOf(item);if(i<0)return;state[side].splice(i,1);item.node.destroy();if(selected===item)select(null);list();warning();layer.batchDraw();changed();}
  function move(item,direction){const arr=state[side],i=arr.indexOf(item),j=i+direction;if(j<0||j>=arr.length)return;[arr[i],arr[j]]=[arr[j],arr[i]];drawSide();select(item);changed();}
  stage.on('click tap',e=>{if(e.target===stage||e.target.getLayer()===backdrop)select(null)});
  document.querySelectorAll('[data-side]').forEach(button=>button.addEventListener('click',()=>{if(button.dataset.side===side)return;side=button.dataset.side;document.querySelectorAll('[data-side]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.side===side)));drawSide();}));
  function shapeNode(shapeId,attrs){const base={...attrs,fill:'#171717',draggable:true};if(shapeId==='circle')return new Konva.Circle({...base,radius:50,offsetX:-50,offsetY:-50});if(shapeId==='square')return new Konva.Rect({...base,width:100,height:100});if(shapeId==='triangle')return new Konva.Line({...base,points:[50,0,100,100,0,100],closed:true});return new Konva.Star({...base,numPoints:5,innerRadius:23,outerRadius:50,offsetX:-50,offsetY:-50});}
  function canAdd(){if(all().length>=100){$('preset-status').textContent='В дизайне может быть не больше 100 элементов.';return false;}return true;}
  document.querySelectorAll('[data-shape]').forEach(button=>button.addEventListener('click',()=>{if(!canAdd())return;const shapeId=button.dataset.shape;if(!['circle','square','triangle','star'].includes(shapeId))return;addItem('shape',shapeNode(shapeId,{x:200,y:240}),{shapeId,name:button.textContent.trim()});changed();}));
  const presetIds=['minimal-typography','urban-motion','retro-sunset','abstract-geometry','vintage-sport','botanical','japanese-graphic','cyber-typography','skate-culture','monochrome-art','cosmic-orbit','minimal-logo'];
  document.querySelectorAll('[data-preset]').forEach(button=>button.addEventListener('click',async()=>{if(!canAdd()||previewMode||saving||restoring||historyBusy)return;const where=side,presetId=button.dataset.preset;if(!presetIds.includes(presetId))return;pendingChange(1);try{const img=await imageFromUrl(`/images/presets/${presetId}.svg`);images.set('preset:'+presetId,img);if(!canAdd()||previewMode||saving||restoring||historyBusy)return;addItem('preset',new Konva.Image({image:img,x:165,y:230,width:170,height:170,draggable:true}),{presetId,name:button.querySelector('.preset-name')?.innerText.replace('＋','').trim()||'Принт'},where);changed();$('preset-status').textContent='Принт добавлен.';}catch(e){$('preset-status').textContent='Не удалось загрузить принт.';}finally{pendingChange(-1);}}));
  $('add-text').addEventListener('click',()=>{if(!canAdd())return;addItem('text',new Konva.Text({x:165,y:230,width:170,text:'Твой текст',fontSize:25,fontFamily:'Manrope',fontStyle:'normal',align:'left',letterSpacing:0,fill:'#171717',draggable:true}));changed();});
  $('delete-element').addEventListener('click',()=>remove(selected));
  function changeProps(){if(!selected)return;const n=selected.node,old={...geometry(n),text:n.text?.(),fontSize:n.fontSize?.(),fill:n.fill?.(),fontFamily:n.fontFamily?.(),fontStyle:n.fontStyle?.(),align:n.align?.(),letterSpacing:n.letterSpacing?.()};if(selected.type==='text'){n.text($('text-content').value);n.fontSize(Math.max(8,Math.min(200,Number($('font-size').value)||8)));n.fill($('text-color').value);n.fontFamily($('font-family').value);n.fontStyle($('font-weight').value);n.align($('text-align').value);n.letterSpacing(Math.max(-5,Math.min(20,Number($('letter-spacing').value)||0)));}else if(selected.type==='shape')n.fill($('text-color').value);n.position({x:Number($('position-x').value)||0,y:Number($('position-y').value)||0});const scale=Math.max(.05,Math.min(5,(Number($('scale').value)||100)/100));n.scale({x:scale,y:scale});n.rotation(Number($('rotation').value)||0);if(!inside(selected)){n.setAttrs(Object.fromEntries(Object.entries(old).filter(([,v])=>v!==undefined)));transformer.forceUpdate();layer.batchDraw();status('Объект должен оставаться внутри области печати.',true);controls();return;}transformer.forceUpdate();list();warning();layer.batchDraw();changed();}
  ['text-content','font-size','text-color','font-family','font-weight','text-align','letter-spacing','position-x','position-y','scale','rotation'].forEach(id=>$(id)?.addEventListener('input',changeProps));
  $('variant').value=root.dataset.selectedVariant || ''; $('variant').addEventListener('change',()=>{changed(false);updateGarment();updatePrice();updateCart();});$('design-title').addEventListener('input',()=>changed(false));
  $('image-file').addEventListener('change',async e=>{const file=e.target.files[0],where=side;if(!file||!canAdd()||previewMode||saving||restoring||historyBusy)return;const info=$('upload-status');if(!['image/png','image/jpeg'].includes(file.type)||file.size>10*1024*1024){info.textContent='Выберите PNG или JPEG размером до 10 МБ.';e.target.value='';return;}const url=URL.createObjectURL(file),localKey=uuid();pendingChange(1);try{const image=await imageFromUrl(url);if(!image.naturalWidth||!image.naturalHeight||image.naturalWidth*image.naturalHeight>20000000||image.naturalWidth>10000||image.naturalHeight>10000)throw new Error('Изображение повреждено или его разрешение слишком велико.');if(!canAdd()||previewMode||saving||restoring||historyBusy){URL.revokeObjectURL(url);return;}objectUrls.set(localKey,{url,file});images.set('local:'+localKey,image);const ratio=Math.min(170/image.naturalWidth,170/image.naturalHeight);addItem('image',new Konva.Image({image,x:165,y:230,width:Math.round(image.naturalWidth*ratio),height:Math.round(image.naturalHeight*ratio),draggable:true}),{localKey,name:'Изображение'},where);info.textContent=auth?'Изображение добавлено. Оно загрузится при сохранении.':'Изображение добавлено локально.';changed();}catch(err){URL.revokeObjectURL(url);info.textContent=err.message||'Не удалось открыть изображение.';}finally{pendingChange(-1);e.target.value='';}});
  async function upload(item){if(item.fileId)return;const local=objectUrls.get(item.localKey);if(!local)throw new Error('Изображение недоступно. Добавьте его снова.');const form=new FormData();form.append('file',local.file);const response=await fetch('/api/designs/uploads',{method:'POST',headers:csrf(),body:form});if(!response.ok)throw new Error(response.status===401?'Сессия закончилась. Войдите снова.':'Не удалось загрузить изображение.');item.fileId=(await response.json()).fileId;}
  async function save(){if(saving||restoring||loadFailed||historyBusy||pendingAssets>0)return;const title=$('design-title').value.trim();if(!title){status('Укажите название дизайна.',true);$('design-title').focus();return;}if(!$('variant').value){status('Выберите доступный размер и цвет.',true);$('variant').focus();return;}const savedRevision=revision;saving=true;root.inert=true;$('save-design').disabled=true;status('Сохранение…');updateCart();try{for(const item of all())if(item.type==='image')await upload(item);const editorState={version:2,front:state.front.map(i=>serialize(i)),back:state.back.map(i=>serialize(i)),productId:Number(root.dataset.productId),variantId:Number($('variant').value)};const response=await fetch(designId?`/api/designs/${encodeURIComponent(designId)}`:'/api/designs',{method:designId?'PUT':'POST',headers:{...csrf(),'Content-Type':'application/json'},body:JSON.stringify({title,editorState})});if(!response.ok)throw new Error(response.status===409?'Дизайн уже добавлен в корзину. Сначала удалите позицию из корзины.':response.status===401||response.status===403?'Сессия закончилась. Войдите снова.':'Не удалось сохранить дизайн. Попробуйте ещё раз.');const result=await response.json();designId=String(result.id);dirty=revision!==savedRevision;status(dirty?'Есть несохранённые изменения':'Сохранено');}catch(err){status(err.message||'Ошибка сети. Попробуйте ещё раз.',true);}finally{saving=false;root.inert=false;$('save-design').disabled=false;updateCart();}}
  if(auth)$('save-design').addEventListener('click',save);
  function setMode(mode){previewMode=mode==='preview';document.querySelector('.studio-tools').inert=previewMode;document.querySelector('.studio-rail').inert=previewMode;document.querySelector('.studio-bottom-tools').inert=previewMode;$("variant").disabled=previewMode;$("design-title").disabled=previewMode;$('mode-edit').setAttribute('aria-pressed',String(!previewMode));$('mode-preview').setAttribute('aria-pressed',String(previewMode));updateHistoryButtons();drawSide();}
  $('mode-edit').addEventListener('click',()=>setMode('edit'));$('mode-preview').addEventListener('click',()=>setMode('preview'));
  $('preview-button').addEventListener('click',()=>{const oldSide=side,oldMode=previewMode;previewMode=true;transformer.visible(false);for(const value of ['front','back']){side=value;drawSide();$('preview-'+value).src=stage.toDataURL({pixelRatio:1});}side=oldSide;previewMode=oldMode;transformer.visible(true);drawSide();$('preview').hidden=false;$('preview').scrollIntoView({behavior:'smooth',block:'nearest'});});
  async function nodeFromData(data,version){const attrs={x:data.x,y:data.y,scaleX:data.scaleX,scaleY:data.scaleY,rotation:data.rotation,draggable:true};if(data.type==='text'){const dimensions=version===1||data.autoWidth===true?{}:(data.width!=null?{width:data.width}:{width:170});return new Konva.Text({...attrs,...dimensions,text:data.text,fontSize:data.fontSize,fill:data.color,fontFamily:data.fontFamily||(version===1?'Arial':'Manrope'),fontStyle:data.fontStyle||'normal',align:data.align||'left',letterSpacing:data.letterSpacing||0});}if(data.type==='shape'){const n=shapeNode(data.shapeId,attrs);n.fill(data.color);return n;}let key,url;if(data.type==='preset'){key='preset:'+data.presetId;url=`/images/presets/${data.presetId}.svg`;}else if(data.type==='image'){key=data.localKey?'local:'+data.localKey:'file:'+data.fileId;url=data.localKey?objectUrls.get(data.localKey)?.url:`/api/designs/uploads/${encodeURIComponent(data.fileId)}`;}else throw new Error('Неизвестный тип слоя');if(!url)throw new Error('Исходное изображение недоступно');let img=images.get(key);if(!img){img=await imageFromUrl(url);images.set(key,img);}const limit=version===1?180:170,ratio=data.type==='preset'?null:Math.min(limit/img.naturalWidth,limit/img.naturalHeight);return new Konva.Image({...attrs,image:img,width:data.width??(ratio?Math.round(img.naturalWidth*ratio):170),height:data.height??(ratio?Math.round(img.naturalHeight*ratio):170)});}
  async function loadSnapshot(snapshot){const nodes={front:[],back:[]};for(const value of ['front','back'])for(const data of snapshot[value])nodes[value].push({data,node:await nodeFromData(data,snapshot.version||2)});for(const item of all())item.node.destroy();state.front=[];state.back=[];for(const value of ['front','back'])for(const {data,node} of nodes[value])addItem(data.type,node,{...data,id:data.id||uuid(),name:data.name||({text:'Текст',image:'Изображение',shape:'Фигура',preset:'Принт'}[data.type]),visible:data.visible!==false,autoWidth:data.type==='text'&&(snapshot.version===1||data.autoWidth===true)},value);drawSide();}
  async function historyGo(delta){const next=historyIndex+delta;if(previewMode||historyBusy||pendingAssets>0||next<0||next>=history.length)return;historyBusy=true;updateHistoryButtons();root.inert=true;try{await loadSnapshot(history[next]);historyIndex=next;changed(false);}catch(e){status('Не удалось восстановить действие: '+e.message,true);}finally{historyBusy=false;root.inert=false;updateHistoryButtons();updateCart();}}
  $('undo-action').addEventListener('click',()=>historyGo(-1));$('redo-action').addEventListener('click',()=>historyGo(1));
  window.addEventListener('keydown',e=>{if(saving||restoring||historyBusy)return;const target=e.target;if(target instanceof Element&&(target.closest('input,textarea,select,[contenteditable="true"]')))return;if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'){e.preventDefault();historyGo(e.shiftKey?1:-1);return;}if((e.key==='Delete'||e.key==='Backspace')&&selected&&!previewMode){e.preventDefault();remove(selected);}});
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  window.addEventListener('pagehide',e=>{if(!e.persisted)objectUrls.forEach(v=>URL.revokeObjectURL(v.url));});
  async function restore(){if(!designId){checkpoint();return;}root.inert=true;if($('save-design'))$('save-design').disabled=true;updateCart();status('Загружаем дизайн…');try{const response=await fetch(`/api/designs/${encodeURIComponent(designId)}`);if(!response.ok)throw new Error('Не удалось открыть дизайн. Проверьте доступ и попробуйте снова.');const design=await response.json();if(Number(design.editorState.productId)!==Number(root.dataset.productId))throw new Error('Дизайн относится к другому товару.');$('design-title').value=design.title;$('variant').value=String(design.editorState.variantId);await loadSnapshot(design.editorState);dirty=false;status('Сохранено');checkpoint();updatePrice();updateCart();}catch(err){loadFailed=true;status((err.message||'Ошибка загрузки.')+' Сохранение отключено, чтобы не перезаписать дизайн.',true);}finally{restoring=false;root.inert=false;if($('save-design'))$('save-design').disabled=loadFailed;updateCart();}}
  updateGarment();updatePrice();updateCart();restore();
})();








