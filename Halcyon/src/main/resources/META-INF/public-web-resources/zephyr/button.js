export function createButton(options) {
  let myButton = document.createElement("button");
  myButton.id = options.id;
  myButton.innerHTML = options.innerHtml;
  myButton.title = options.title;
  myButton.classList.add("annotationBtn");

  let canvas = document.querySelector('canvas');
  document.body.insertBefore(myButton, canvas);

  return myButton;
}
