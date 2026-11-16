document.addEventListener('DOMContentLoaded', () => {
  const add = document.getElementById('add-date');
  const list = document.getElementById('date-list');
  if (!add || !list) return;
  add.addEventListener('click', () => {
    if (list.children.length >= 7) return;
    const row = document.createElement('div'); row.className = 'date-row';
    const input = document.createElement('input'); input.type = 'date'; input.name = 'dates'; input.required = true;
    input.className = 'form-control'; input.min = add.dataset.min; input.setAttribute('aria-label', '추가 후보 날짜');
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'btn btn-outline-secondary'; remove.textContent = '삭제';
    remove.addEventListener('click', () => {row.remove(); add.disabled = false;});
    row.append(input, remove); list.append(row); add.disabled = list.children.length >= 7;
  });
});
