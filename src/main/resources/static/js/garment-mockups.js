(() => {
  'use strict';
  const sources = {
    TSHIRT: {front: '/images/editor/tshirt-front-white.png', back: '/images/editor/tshirt-back-white.png'},
    HOODIE: {front: '/images/editor/hoodie-front-generated.png', back: '/images/editor/hoodie-back-generated.png'},
    SWEATSHIRT: {front: '/images/editor/sweatshirt-front-generated.png', back: '/images/editor/sweatshirt-back-generated.png'}
  };
  const palettes = {
    Black: {base: [28, 29, 28], light: [76, 77, 74]},
    Grey: {base: [94, 97, 94], light: [201, 203, 198]},
    Beige: {base: [143, 122, 99], light: [234, 220, 197]}
  };
  const namedColors = {white:'#ffffff', black:'#343434', grey:'#9a9c98', gray:'#9a9c98', beige:'#d2bea1', red:'#b64646', blue:'#496894', green:'#58785c', navy:'#334460', pink:'#da9eac', brown:'#8b6855', orange:'#d99055', purple:'#806890', красный:'#b64646', синий:'#496894', зелёный:'#58785c', зеленый:'#58785c', чёрный:'#343434', черный:'#343434', белый:'#ffffff', серый:'#9a9c98', бежевый:'#d2bea1'};
  function paletteFor(value) {
    if (Object.hasOwn(palettes, value)) return palettes[value];
    const raw = typeof value === 'string' ? value.trim().toLowerCase() : '';
    const hex = namedColors[raw] || raw;
    if (!/^#[0-9a-f]{6}$/i.test(hex)) return null;
    const rgb = [1,3,5].map(i => parseInt(hex.slice(i,i+2),16));
    return {base:rgb.map(n=>Math.round(n*.55)),light:rgb.map(n=>Math.round(n+(255-n)*.22))};
  }
  const cache = new Map();
  function imageFromLocal(src) {
    return new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error('Не удалось загрузить изображение одежды.'));
      image.src = src;
    });
  }
  async function create(category, side, color) {
    const image = await imageFromLocal(sources[category][side]);
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    const context = canvas.getContext('2d', {willReadFrequently: color !== 'White'});
    if (!context) throw new Error('Не удалось создать изображение одежды.');
    context.drawImage(image, 0, 0);
    if (color === 'White') return canvas;
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
    const data = pixels.data, palette = paletteFor(color);
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] === 0) continue;
      const luminance = (0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2]) / 255;
      for (let channel = 0; channel < 3; channel++) {
        data[i + channel] = Math.round(palette.base[channel] + (palette.light[channel] - palette.base[channel]) * luminance);
      }
    }
    context.putImageData(pixels, 0, 0);
    return canvas;
  }
  window.PrintlabMockups = Object.freeze({
    load(category, side, color) {
      const normalizedSide = typeof side === 'string' ? side.toLowerCase() : '';
      if (!Object.hasOwn(sources, category) || !Object.hasOwn(sources[category], normalizedSide) || (color !== 'White' && !paletteFor(color))) return Promise.resolve(null);
      const key = `${category}:${normalizedSide}:${color}`;
      if (!cache.has(key)) {
        const request = create(category, normalizedSide, color).catch(error => { cache.delete(key); throw error; });
        cache.set(key, request);
      }
      return cache.get(key);
    }
  });
})();
