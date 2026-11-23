(() => {
  'use strict';
  const presets=new Set(['minimal-typography','urban-motion','retro-sunset','abstract-geometry','vintage-sport','botanical','japanese-graphic','cyber-typography','skate-culture','monochrome-art','cosmic-orbit','minimal-logo']);
  const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i, colors=/^#[0-9a-f]{6}$/i;
  const image=src=>new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=()=>reject(new Error('Изображение недоступно'));i.src=src;});
  const number=(v,fallback,min,max)=>{const n=v==null?fallback:v;if(typeof n!=='number'||!Number.isFinite(n)||n<min||n>max)throw new Error('Некорректные параметры слоя');return n;};
  async function makeNode(data,version){
    if(!data||typeof data!=='object'||!['text','image','preset','shape'].includes(data.type))throw new Error('Неизвестный слой');
    const a={x:number(data.x,0,-10000,10000),y:number(data.y,0,-10000,10000),scaleX:number(data.scaleX,1,.000001,100),scaleY:number(data.scaleY,1,.000001,100),rotation:number(data.rotation,0,-36000,36000),listening:false};
    if(data.type==='text'){
      if(typeof data.text!=='string'||data.text.length>500||!colors.test(data.color))throw new Error('Некорректный текст');
      const fonts=['Arial','Manrope','Georgia','Times New Roman','Courier New'];
      return new Konva.Text({...a,text:data.text,fontSize:number(data.fontSize,30,8,300),fill:data.color,fontFamily:fonts.includes(data.fontFamily)?data.fontFamily:(version===1?'Arial':'Manrope'),fontStyle:['normal','bold','italic','bold italic'].includes(data.fontStyle)?data.fontStyle:'normal',align:['left','center','right'].includes(data.align)?data.align:'left',letterSpacing:number(data.letterSpacing,0,-5,20),...(version===2&&data.autoWidth!==true?{width:number(data.width,170,0,10000)}:{})});
    }
    if(data.type==='shape'){
      if(!colors.test(data.color))throw new Error('Некорректная фигура');const attrs={...a,fill:data.color};
      if(data.shapeId==='circle')return new Konva.Circle({...attrs,radius:50,offsetX:-50,offsetY:-50});
      if(data.shapeId==='square')return new Konva.Rect({...attrs,width:100,height:100});
      if(data.shapeId==='triangle')return new Konva.Line({...attrs,points:[50,0,100,100,0,100],closed:true});
      if(data.shapeId==='star')return new Konva.Star({...attrs,numPoints:5,innerRadius:23,outerRadius:50,offsetX:-50,offsetY:-50});
      throw new Error('Неизвестная фигура');
    }
    let src;
    if(data.type==='image'){
      if(typeof data.fileId!=='string'||!uuid.test(data.fileId))throw new Error('Некорректный файл');
      src='/api/designs/uploads/'+encodeURIComponent(data.fileId);
    }else if(presets.has(data.presetId))src='/images/presets/'+data.presetId+'.svg';
    else if(typeof data.presetId==='string'&&data.presetId.startsWith('admin:')&&uuid.test(data.presetId.slice(6)))src='/admin-prints/'+encodeURIComponent(data.presetId.slice(6))+'/image';
    else throw new Error('Неизвестный принт');
    const img=await image(src),limit=version===1?180:170,ratio=data.type==='preset'?null:Math.min(limit/img.naturalWidth,limit/img.naturalHeight);
    return new Konva.Image({...a,image:img,width:number(data.width,ratio?Math.round(img.naturalWidth*ratio):170,1,10000),height:number(data.height,ratio?Math.round(img.naturalHeight*ratio):170,1,10000)});
  }
  async function setup(article){
    const box=article.querySelector('.cart-design-preview'),controls=article.querySelector('.cart-preview-controls'),status=article.querySelector('.cart-preview-status');if(!box||!controls||!status)return;
    let stage=null,observer=null,resizeListener=null,renderId=0;
    function stopResize(){if(observer){observer.disconnect();observer=null;}if(resizeListener){window.removeEventListener('resize',resizeListener);resizeListener=null;}}
    function fallback(){stopResize();if(stage){stage.destroy();stage=null;}box.hidden=true;controls.hidden=true;status.textContent='Предпросмотр дизайна недоступен. Фото товара показано, если оно доступно.';}
    try{
      if(!window.Konva||!window.PrintlabMockups||!/^[1-9]\d*$/.test(article.dataset.designId||''))throw new Error('Предпросмотр недоступен');
      const response=await fetch('/api/designs/'+encodeURIComponent(article.dataset.designId),{credentials:'same-origin'});if(!response.ok)throw new Error('Дизайн недоступен');
      const data=await response.json(),snapshot=data.editorState;
      if(!snapshot||![1,2].includes(snapshot.version)||!Array.isArray(snapshot.front)||!Array.isArray(snapshot.back)||snapshot.front.length+snapshot.back.length>100)throw new Error('Некорректный дизайн');
      const visible=side=>snapshot[side].filter(layer=>layer&&layer.visible!==false);
      async function draw(side){
        const current=++renderId;status.textContent='Загружаем предварительный вид…';
        try{
          const [mockup,nodes]=await Promise.all([window.PrintlabMockups.load(article.dataset.category,side,article.dataset.color),Promise.all(visible(side).map(layer=>makeNode(layer,snapshot.version)))]);
          if(!mockup)throw new Error('Мокап недоступен');if(current!==renderId)return;
          stopResize();if(stage)stage.destroy();box.replaceChildren();box.hidden=false;
          const size=box.clientWidth;if(!size)throw new Error('Невозможно отобразить');const scale=size/500;
          stage=new Konva.Stage({container:box,width:size,height:620*scale,scaleX:scale,scaleY:scale,listening:false});const layer=new Konva.Layer({listening:false});stage.add(layer);
          const fit=Math.min(560/mockup.width,400/mockup.height),w=mockup.width*fit,h=mockup.height*fit;
          layer.add(new Konva.Image({image:mockup,x:(500-w)/2,y:90,width:w,height:h,listening:false}));nodes.forEach(node=>layer.add(node));layer.draw();
          const resize=()=>{if(!stage)return;const width=box.clientWidth;if(!width)return;const factor=width/500;stage.width(width);stage.height(620*factor);stage.scale({x:factor,y:factor});stage.draw();};if(typeof ResizeObserver==='function'){observer=new ResizeObserver(resize);observer.observe(box);}else{resizeListener=resize;window.addEventListener('resize',resizeListener);}
          controls.hidden=false;controls.querySelectorAll('[data-preview-side]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.previewSide===side)));status.textContent='Предварительный вид: '+(side==='front'?'перед':'спина')+'. Цвет может отличаться.';
        }catch(_){if(current===renderId)fallback();}
      }
      controls.querySelectorAll('[data-preview-side]').forEach(button=>button.addEventListener('click',()=>draw(button.dataset.previewSide)));
      await draw(!visible('front').length&&visible('back').length?'back':'front');
    }catch(_){fallback();}
    window.addEventListener('pagehide',e=>{if(!e.persisted){stopResize();if(stage){stage.destroy();stage=null;}}});
  }
  document.querySelectorAll('.cart-line[data-design-id]').forEach(setup);
})();
