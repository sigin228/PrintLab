(() => {
  'use strict';
  const presets = new Set(['minimal-typography','urban-motion','retro-sunset','abstract-geometry','vintage-sport','botanical','japanese-graphic','cyber-typography','skate-culture','monochrome-art','cosmic-orbit','minimal-logo']);
  const colors = /^#[0-9a-f]{6}$/i;
  const load = src => new Promise((resolve, reject) => { const img = new Image(); img.onload = () => resolve(img); img.onerror = reject; img.src = src; });
  const number = (v, fallback, min, max) => { const n = v == null ? fallback : v; if (typeof n !== 'number' || !Number.isFinite(n) || n < min || n > max) throw Error('invalid number'); return n; };
  async function node(data, version, prefix) {
    if (!data || typeof data !== 'object' || !['text','image','preset','shape'].includes(data.type)) throw Error('invalid layer');
    const attrs = { x:number(data.x,0,-10000,10000), y:number(data.y,0,-10000,10000), scaleX:number(data.scaleX,1,.000001,100), scaleY:number(data.scaleY,1,.000001,100), rotation:number(data.rotation,0,-36000,36000), listening:false };
    if(data.type === 'text') {
      if(typeof data.text !== 'string' || data.text.length > 500 || !colors.test(data.color)) throw Error('invalid text');
      const fonts = ['Arial','Manrope','Georgia','Times New Roman','Courier New'];
      return new Konva.Text({...attrs,text:data.text,fontSize:number(data.fontSize,30,8,300),fill:data.color,fontFamily:fonts.includes(data.fontFamily)?data.fontFamily:'Arial',fontStyle:['normal','bold','italic','bold italic'].includes(data.fontStyle)?data.fontStyle:'normal',align:['left','center','right'].includes(data.align)?data.align:'left',letterSpacing:number(data.letterSpacing,0,-5,20),...(version===2 && data.autoWidth!==true && data.width!=null ? {width:number(data.width,170,0,10000)} : {})});
    }
    if(data.type === 'shape') {
      if(!colors.test(data.color)) throw Error('invalid shape');
      const a={...attrs,fill:data.color};
      if(data.shapeId==='circle') return new Konva.Circle({...a,radius:50,offsetX:-50,offsetY:-50});
      if(data.shapeId==='square') return new Konva.Rect({...a,width:100,height:100});
      if(data.shapeId==='triangle') return new Konva.Line({...a,points:[50,0,100,100,0,100],closed:true});
      if(data.shapeId==='star') return new Konva.Star({...a,numPoints:5,innerRadius:23,outerRadius:50,offsetX:-50,offsetY:-50});
      throw Error('unknown shape');
    }
    let src;
    if(data.type==='preset') {
      if (presets.has(data.presetId)) src='/images/presets/'+data.presetId+'.svg';
      else if (typeof data.presetId==='string' && /^admin:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.presetId)) src='/admin-prints/'+encodeURIComponent(data.presetId.slice(6))+'/image';
      else throw Error('invalid preset');
    }
    else { if(typeof data.fileId!=='string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.fileId)) throw Error('invalid file'); src=prefix+encodeURIComponent(data.fileId); }
    const image=await load(src), ratio=Math.min(180/image.naturalWidth,180/image.naturalHeight);
    return new Konva.Image({...attrs,image,width:number(data.width,data.type==='preset'?170:Math.round(image.naturalWidth*ratio),1,10000),height:number(data.height,data.type==='preset'?170:Math.round(image.naturalHeight*ratio),1,10000)});
  }
  async function render(container) {
    try {
      if(!window.Konva) throw Error('missing renderer');
      const snapshot=JSON.parse(container.dataset.snapshot), order=container.dataset.orderId;
      if(!snapshot || ![1,2].includes(snapshot.version) || !Array.isArray(snapshot.front) || !Array.isArray(snapshot.back) || snapshot.front.length+snapshot.back.length>100 || !/^[1-9]\d*$/.test(order)) throw Error('invalid snapshot');
      const prefix=(container.dataset.admin==='true'?'/admin/orders/':'/orders/')+order+'/images/';
      const built = await Promise.all(['front','back'].map(async side => Promise.all(snapshot[side].filter(i=>i && i.visible!==false).map(i=>node(i,snapshot.version,prefix)))));
      const grid=document.createElement('div'); grid.className='snapshot-grid';
      ['Перед','Спина'].forEach((label,index)=>{ const figure=document.createElement('figure'), canvas=document.createElement('div'), caption=document.createElement('figcaption'); caption.textContent=label; canvas.className='snapshot-canvas'; figure.append(canvas,caption); grid.append(figure); const stage=new Konva.Stage({container:canvas,width:160,height:198.4,scaleX:.32,scaleY:.32}); const layer=new Konva.Layer({listening:false}); stage.add(layer); layer.add(new Konva.Rect({x:140,y:155,width:220,height:330,fill:'#fff',stroke:'#c5c9bd',dash:[5,5]})); built[index].forEach(n=>layer.add(n)); layer.draw(); });
      container.replaceChildren(grid);
    } catch (_) { container.textContent='Не удалось показать предварительный вид дизайна.'; }
  }
  document.querySelectorAll('.order-snapshot').forEach(render);
})();
