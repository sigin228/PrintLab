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
  backdrop.add(new Konva.Rect({...bounds,stroke:'#737b69',strokeWidth:1,dash:[7,6]}));
  const transformer=new Konva.Transformer({rotateEnabled:true,keepRatio:true,enabledAnchors:['top-left','top-right','bottom-left','bottom-right'],borderStroke:'#485200',anchorStroke:'#485200',anchorFill:'#d9ff43',anchorSize:9});layer.add(transformer);
  const status=(message,error=false)=>{if($('save-status')){$('save-status').textContent=message;$('save-status').classList.toggle('editor-error',error)}};
  function changed(){revision++;dirty=true;status('Есть несохранённые изменения');updatePrice();updateCart();}
  function resize(){const w=$('stage-container').clientWidth;const scale=w/W;stage.width(w);stage.height(H*scale);stage.scale({x:scale,y:scale});stage.draw();}
  new ResizeObserver(resize).observe($('stage-container'));resize();
  function updatePrice(){const option=$('variant').selectedOptions[0],price=Number(option?.dataset.price);$('total-price').textContent=option?.value ? `${(price+(state.front.length+state.back.length?1000:0)).toLocaleString('ru-RU')} ₸`:'—';$('variant-info').textContent=option?.value?`Доступно: ${option.dataset.stock} шт.`:'Выберите доступный вариант.';}
  function updateCart(){const form=$('add-to-cart');if(!form)return;form.querySelector('[name=variantId]').value=$('variant').value;form.querySelector('[name=designId]').value=designId;form.querySelector('button').disabled=!designId||dirty||!$('variant').value||saving||restoring||loadFailed;}
  function warning(){let outside=false;for(const item of state[side]){const r=item.node.getClientRect({relativeTo:layer,skipShadow:true});if(r.x<bounds.x||r.y<bounds.y||r.x+r.width>bounds.x+bounds.width||r.y+r.height>bounds.y+bounds.height)outside=true;}$('bounds-warning').hidden=!outside;}
  function controls(){const active=!!selected; $('element-controls').disabled=!active;const n=selected?.node;const isText=selected?.type==='text';['text-content','font-size','text-color'].forEach(id=>$(id).disabled=!isText);if(n){$('position-x').value=Math.round(n.x());$('position-y').value=Math.round(n.y());$('scale').value=Math.round(n.scaleX()*100);$('rotation').value=Math.round(n.rotation());if(isText){$('text-content').value=n.text();$('font-size').value=n.fontSize();$('text-color').value=n.fill();}}}
  function list(){const ul=$('element-list');ul.replaceChildren();$('empty-elements').hidden=state[side].length>0;state[side].forEach((item,i)=>{const li=document.createElement('li'),button=document.createElement('button');button.type='button';button.textContent=(item.type==='text'?'Текст: '+item.node.text().slice(0,40):'Изображение '+(i+1));button.setAttribute('aria-pressed',String(item===selected));button.addEventListener('click',()=>select(item));li.append(button);ul.append(li)});}
  function select(item){selected=item;transformer.nodes(item?[item.node]:[]);transformer.moveToTop();controls();list();layer.batchDraw();}
  function addItem(type, node, data={}){const item={type,node,...data,key:nextId++};state[side].push(item);layer.add(node);node.on('click tap',()=>select(item));node.on('dragmove transform',()=>{controls();warning();});node.on('dragend transformend',()=>{changed();warning();});select(item);return item;}
  function drawSide(){updateGarment();layer.getChildren().filter(n=>n!==transformer).forEach(n=>n.remove());for(const item of state[side])layer.add(item.node);select(null);warning();layer.draw();}
  stage.on('click tap',e=>{if(e.target===stage||e.target.getLayer()===backdrop)select(null)});
  document.querySelectorAll('[data-side]').forEach(button=>button.addEventListener('click',()=>{if(button.dataset.side===side)return;side=button.dataset.side;document.querySelectorAll('[data-side]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.side===side)));drawSide();}));
  // Пресеты состоят только из обычных текстовых элементов существующего формата.
  const presets = {
    sun:{name:'Солнце',parts:[{x:207,y:185,text:'☼',fontSize:100,color:'#a45f27'},{x:172,y:310,text:'СВЕТ ВНУТРИ',fontSize:17,color:'#171717'},{x:207,y:337,text:'✳ 01 ✳',fontSize:12,color:'#a45f27'}]},
    star:{name:'Звезда',parts:[{x:205,y:184,text:'✳',fontSize:100,color:'#677700'},{x:164,y:310,text:'СИЯЙ ПО-СВОЕМУ',fontSize:15,color:'#171717'},{x:203,y:335,text:'★  /  ★',fontSize:13,color:'#677700'}]},
    orbit:{name:'Орбита',parts:[{x:205,y:188,text:'◎',fontSize:100,color:'#304a5c'},{x:174,y:311,text:'СВОЯ ОРБИТА',fontSize:17,color:'#171717'},{x:210,y:337,text:'01 / 24',fontSize:12,color:'#304a5c'}]},
    own: {name:'Свой путь',parts:[{x:165,y:207,text:'YOUR LIFE / YOUR RULES',fontSize:10,color:'#171717'},{x:165,y:230,text:'СВОЙ\nПУТЬ.',fontSize:31,color:'#171717'},{x:165,y:307,text:'✳ 01 / 01',fontSize:14,color:'#7a8500'}]},
    move:{name:'В движении',parts:[{x:160,y:208,text:'KEEP MOVING / 002',fontSize:10,color:'#171717'},{x:160,y:231,text:'В\nДВИЖЕНИИ',fontSize:25,color:'#171717'},{x:160,y:303,text:'↗ ВПЕРЁД',fontSize:14,color:'#687600'}]},
    create:{name:'Создавай',parts:[{x:160,y:208,text:'CREATE EVERY DAY',fontSize:10,color:'#171717'},{x:160,y:231,text:'СОЗДАВАЙ\nСВОЁ',fontSize:25,color:'#171717'},{x:160,y:303,text:'✳ ✳ ✳',fontSize:16,color:'#a07337'}]},
    city:{name:'Город',parts:[{x:165,y:208,text:'URBAN / EVERYDAY',fontSize:10,color:'#171717'},{x:165,y:231,text:'ГОРОД\nВНУТРИ',fontSize:28,color:'#171717'},{x:165,y:305,text:'01 — 24',fontSize:14,color:'#777777'}]}
  };
  document.querySelectorAll('[data-preset]').forEach(button=>button.addEventListener('click',()=>{
    const preset=presets[button.dataset.preset];if(!preset)return;
    if(state.front.length+state.back.length+preset.parts.length>100){$('preset-status').textContent='В дизайне может быть не больше 100 элементов. Удалите лишние элементы.';return;}
    let title;const dark=$('variant').selectedOptions[0]?.dataset.color==='Black';for(const [i,part] of preset.parts.entries()){const color=dark?'#f7f6f2':part.color;const item=addItem('text',new Konva.Text({x:part.x,y:part.y,text:part.text,fontSize:part.fontSize,fill:color,draggable:true}));if(i===([ 'sun','star','orbit' ].includes(button.dataset.preset)?0:1))title=item;}
    select(title);changed();warning();$('preset-status').textContent='Принт «'+preset.name+'» добавлен на сторону «'+(side==='front'?'Перед':'Спина')+'». Можно изменить каждый элемент.';
  }));
  $('add-text').addEventListener('click',()=>{addItem('text',new Konva.Text({x:165,y:230,text:'Твой текст',fontSize:30,fill:'#171717',draggable:true}));changed();warning();});
  $('delete-element').addEventListener('click',()=>{if(!selected)return;state[side]=state[side].filter(x=>x!==selected);selected.node.destroy();select(null);changed();warning();});
  function changeProps(){if(!selected)return;let n=selected.node;if(selected.type==='text'){n.text($('text-content').value);n.fontSize(Math.max(8,Math.min(200,Number($('font-size').value)||8)));n.fill($('text-color').value);}n.position({x:Number($('position-x').value)||0,y:Number($('position-y').value)||0});const scale=Math.max(.05,Math.min(5,(Number($('scale').value)||100)/100));n.scale({x:scale,y:scale});n.rotation(Number($('rotation').value)||0);transformer.forceUpdate();list();warning();layer.batchDraw();changed();}
  ['text-content','font-size','text-color','position-x','position-y','scale','rotation'].forEach(id=>$(id).addEventListener('input',changeProps));
  $('variant').value=root.dataset.selectedVariant || ''; $('variant').addEventListener('change',()=>{changed();updateGarment();updatePrice();updateCart();});$('design-title').addEventListener('input',changed);
  const csrf=()=>({[document.querySelector('meta[name=csrf-header]').content]:document.querySelector('meta[name=csrf-token]').content});
  async function imageFromUrl(url){return new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=()=>reject(new Error('Не удалось открыть изображение.'));image.src=url;});}
  $('image-file').addEventListener('change',async e=>{const file=e.target.files[0];if(!file)return;const info=$('upload-status');if(!['image/png','image/jpeg'].includes(file.type)||file.size>10*1024*1024){info.textContent='Выберите PNG или JPEG размером до 10 МБ.';e.target.value='';return;}let url=URL.createObjectURL(file);try{const image=await imageFromUrl(url);if(!image.naturalWidth||!image.naturalHeight||image.naturalWidth*image.naturalHeight>20000000)throw new Error('Изображение повреждено или его разрешение слишком велико.');const ratio=Math.min(180/image.naturalWidth,180/image.naturalHeight);const node=new Konva.Image({image,x:160,y:210,width:Math.round(image.naturalWidth*ratio),height:Math.round(image.naturalHeight*ratio),draggable:true});const item=addItem('image',node,{file,fileId:null,objectUrl:url});info.textContent=auth?'Изображение добавлено. Оно загрузится при сохранении.':'Изображение добавлено локально.';changed();warning();}catch(err){URL.revokeObjectURL(url);info.textContent=err.message||'Не удалось открыть изображение.';}e.target.value='';});
  async function upload(item){if(item.fileId)return;const form=new FormData();form.append('file',item.file);const response=await fetch('/api/designs/uploads',{method:'POST',headers:csrf(),body:form});if(!response.ok)throw new Error('Не удалось загрузить изображение. Проверьте файл и попробуйте снова.');const result=await response.json();item.fileId=result.fileId;}
  function serialize(item){const n=item.node,common={type:item.type,x:n.x(),y:n.y(),scaleX:n.scaleX(),scaleY:n.scaleY(),rotation:n.rotation()};return item.type==='text'?{...common,text:n.text(),fontSize:n.fontSize(),color:n.fill()}:{...common,fileId:item.fileId};}
  async function save(){if(saving||restoring||loadFailed)return;const title=$('design-title').value.trim();if(!title){status('Укажите название дизайна.',true);$('design-title').focus();return;}if(!$('variant').value){status('Выберите доступный размер и цвет.',true);$('variant').focus();return;}const savedRevision=revision;saving=true;root.inert=true;$('save-design').disabled=true;status('Сохранение…');updateCart();try{for(const item of [...state.front,...state.back])if(item.type==='image')await upload(item);const editorState={version:1,front:state.front.map(serialize),back:state.back.map(serialize),productId:Number(root.dataset.productId),variantId:$('variant').value?Number($('variant').value):null};const response=await fetch(designId?`/api/designs/${encodeURIComponent(designId)}`:'/api/designs',{method:designId?'PUT':'POST',headers:{...csrf(),'Content-Type':'application/json'},body:JSON.stringify({title,editorState})});if(!response.ok)throw new Error(response.status===409?'Дизайн уже добавлен в корзину. Сначала удалите позицию из корзины, затем сохраните изменения.':response.status===401||response.status===403?'Сессия закончилась. Войдите снова.':'Не удалось сохранить дизайн. Попробуйте ещё раз.');const result=await response.json();designId=String(result.id);dirty=revision!==savedRevision;status(dirty?'Есть несохранённые изменения':'Сохранено');}catch(err){status(err.message||'Ошибка сети. Попробуйте ещё раз.',true);}finally{saving=false;root.inert=false;$('save-design').disabled=false;updateCart();}}
  if(auth)$('save-design').addEventListener('click',save);
  $('preview-button').addEventListener('click',()=>{const old=side;transformer.visible(false);for(const value of ['front','back']){side=value;drawSide();$('preview-'+value).src=stage.toDataURL({pixelRatio:1});}side=old;drawSide();transformer.visible(true);layer.draw();$('preview').hidden=false;$('preview').scrollIntoView({behavior:'smooth',block:'nearest'});});
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  async function restore(){if(!designId)return;root.inert=true;if($('save-design'))$('save-design').disabled=true;updateCart();status('Загружаем дизайн…');try{const response=await fetch(`/api/designs/${encodeURIComponent(designId)}`);if(!response.ok)throw new Error('Не удалось открыть дизайн. Проверьте доступ и попробуйте снова.');const design=await response.json();$('design-title').value=design.title;for(const value of ['front','back']){side=value;for(const data of design.editorState[value]||[]){const attrs={x:data.x,y:data.y,scaleX:data.scaleX,scaleY:data.scaleY,rotation:data.rotation,draggable:true};if(data.type==='text')addItem('text',new Konva.Text({...attrs,text:data.text,fontSize:data.fontSize,fill:data.color}));else if(data.type==='image'){const img=await imageFromUrl(`/api/designs/uploads/${encodeURIComponent(data.fileId)}`),ratio=Math.min(180/img.naturalWidth,180/img.naturalHeight);addItem('image',new Konva.Image({...attrs,image:img,width:Math.round(img.naturalWidth*ratio),height:Math.round(img.naturalHeight*ratio)}),{fileId:data.fileId});}}}side='front';drawSide();dirty=false;status('Сохранено');updatePrice();updateCart();}catch(err){loadFailed=true;status((err.message||'Ошибка загрузки.')+' Сохранение отключено, чтобы не перезаписать дизайн.',true);}finally{restoring=false;root.inert=false;if($('save-design'))$('save-design').disabled=loadFailed;updateCart();}}
  updateGarment();updatePrice();updateCart();restore();
})();

