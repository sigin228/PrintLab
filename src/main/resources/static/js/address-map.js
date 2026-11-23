(() => {
  'use strict';
  document.querySelectorAll('[data-address-picker]').forEach((picker) => {
    const form = picker.closest('form');
    const address = form.querySelector('[name="deliveryAddress"]');
    const latitude = picker.querySelector('[name="deliveryLatitude"]');
    const longitude = picker.querySelector('[name="deliveryLongitude"]');
    const toggle = picker.querySelector('.address-map-toggle');
    const panel = picker.querySelector('.address-map-panel');
    const status = picker.querySelector('.address-map-status');
    let map, marker, sequence = 0;
    address.readOnly = true;
    address.placeholder = 'Выберите дом на карте';
    toggle.addEventListener('click', () => {
      if (!window.L) { status.textContent = 'Не удалось загрузить карту. Обновите страницу.'; panel.hidden = false; return; }
      panel.hidden = !panel.hidden;
      toggle.setAttribute('aria-expanded', String(!panel.hidden));
      if (panel.hidden) return;
      if (!map) {
        const start = latitude.value && longitude.value ? [Number(latitude.value), Number(longitude.value)] : [51.1282, 71.4304];
        map = L.map(picker.querySelector('.address-map'), { scrollWheelZoom: false }).setView(start, latitude.value ? 17 : 12);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' }).addTo(map);
        if (latitude.value && longitude.value) marker = L.marker(start).addTo(map);
        const choosePoint = async (point) => {
          const current = ++sequence;
          latitude.value = ''; longitude.value = ''; address.value = '';
          if (marker) map.removeLayer(marker);
          marker = L.marker(point).addTo(map);
          status.textContent = 'Проверяем адрес…';
          address.setCustomValidity('Дождитесь проверки адреса.');
          try {
            const params = new URLSearchParams({ lat: point.lat.toFixed(7), lon: point.lng.toFixed(7) });
            const response = await fetch('/api/delivery/address?' + params, { headers: { Accept: 'application/json' } });
            const data = await response.json();
            if (current !== sequence) return;
            if (!response.ok || !data.address) throw new Error(data.error || 'Адрес не найден. Выберите дом в Астане.');
            latitude.value = String(data.lat); longitude.value = String(data.lon);
            address.value = data.address;
            address.setCustomValidity('');
            status.textContent = 'Адрес найден. Проверьте его и укажите квартиру при необходимости.';
          } catch (error) {
            if (current !== sequence) return;
            address.setCustomValidity('Выберите дом в Астане на карте.');
            status.textContent = error.message || 'Не удалось проверить адрес. Попробуйте ещё раз.';
          }
        };
        map.on('click', (event) => choosePoint(event.latlng));
        picker.querySelector('.address-map-center').addEventListener('click', () => choosePoint(map.getCenter()));
      }
      requestAnimationFrame(() => map.invalidateSize());
    });
  });
})();
