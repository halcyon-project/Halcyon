export function colorPalette() {
  let colorPalette = document.createElement('select');
  colorPalette.id = 'colorPalette';
  document.body.insertBefore(colorPalette, document.querySelector('canvas'));

  // Create and append the options to the dropdown
  // TODO: Create a function that makes a call to the server for this information
  const options = [
    { value: 'nothing', text: '-- Class --' },
    { value: '#00ff00:Tumor', text: 'Tumor' },
    { value: '#ffff00:Misc', text: 'Misc' },
    { value: '#ff0000:Lymphocyte', text: 'Lymphocyte' },
    { value: '#0000ff:Background', text: 'Background' }
  ];

  options.forEach(opt => {
    const option = document.createElement('option');
    option.value = opt.value;
    option.textContent = opt.text;
    colorPalette.appendChild(option);
  });

  colorPalette.addEventListener('change', (event) => {
    if (event.target.value === 'nothing') {
      window.cancerColor = '';
      window.cancerType = '';
    } else {
      let arr = event.target.value.split(':');
      window.cancerColor = arr[0];
      window.cancerType = arr[1];
      // console.log(window.cancerColor);
      // console.log(window.cancerType);
    }
  });
}
