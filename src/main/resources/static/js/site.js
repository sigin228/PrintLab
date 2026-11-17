document.querySelectorAll('[data-password-toggle]').forEach(button => {
  const input = document.getElementById(button.dataset.passwordToggle);
  if (!input) return;
  button.hidden = false;
  button.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Скрыть' : 'Показать';
    button.setAttribute('aria-pressed', String(show));
  });
});
const password = document.querySelector('#password');
const rules = document.querySelector('[data-password-rules]');
if (password && rules) {
  rules.hidden = false;
  const updateRules = () => {
    const value = password.value;
    const checks = {length: value.length >= 8 && value.length <= 72 && new TextEncoder().encode(value).length <= 72, letters: /[A-Za-zА-Яа-яЁё]/.test(value), digits: /[0-9]/.test(value)};
    rules.querySelectorAll('[data-rule]').forEach(item => {
      const met = checks[item.dataset.rule];
      item.classList.toggle('met', met);
      item.textContent = (met ? '✓ ' : '○ ') + item.textContent.slice(2);
    });
  };
  password.addEventListener('input', updateRules);
}
document.querySelectorAll('form[data-submit-label]').forEach(form => {
  form.addEventListener('submit', () => {
    const button = form.querySelector('button[type="submit"]');
    if (!button) return;
    button.disabled = true;
    const label = button.querySelector('[data-button-label]');
    if (label) { label.dataset.originalLabel = label.textContent; label.textContent = form.dataset.submitLabel; }
  });
});

window.addEventListener('pageshow', () => {
  document.querySelectorAll('form[data-submit-label] button[type="submit"]').forEach(button => {
    button.disabled = false;
    const label = button.querySelector('[data-button-label]');
    if (label && label.dataset.originalLabel) label.textContent = label.dataset.originalLabel;
  });
});
