import { createSlider, createButton } from "./elements.js";
import { tileAdjustments } from "../scene/imageLayer.js";
import { invalidate } from "../renderLoop.js";

/**
 * Brightness / contrast sliders.
 *
 * The sliders write the shared `tileAdjustments` uniforms that every tile
 * material references (see scene/imageLayer.js), so an adjustment applies to
 * all layers at once — including tiles that stream in later — while the
 * materials themselves stay untouched: edge-tile UV transforms, per-layer
 * opacity and feature-layer alpha keep working.
 */
export function brightContrast(scene) {
  let contrastSlider = createSlider({
    id: "contrast",
    title: "<i class=\"fa fa-adjust\" aria-hidden=\"true\" title=\"Contrast\"></i>",
    min: 0,
    max: 4,
    step: 0.01,
    value: tileAdjustments.contrast.value
  });

  let brightnessSlider = createSlider({
    id: "brightness",
    title: "<i class=\"fa fa-sun\" aria-hidden=\"true\" title=\"Brightness\"></i>",
    min: -1,
    max: 1,
    step: 0.01,
    value: tileAdjustments.brightness.value
  });

  let resetButton = createButton({
    id: "brightContrastReset",
    innerHtml: "<i class=\"fa fa-undo\"></i>",
    title: "Reset Brightness and Contrast"
  });

  function updateUniforms() {
    tileAdjustments.contrast.value = parseFloat(contrastSlider.value);
    tileAdjustments.brightness.value = parseFloat(brightnessSlider.value);
    invalidate();
  }

  resetButton.addEventListener("click", () => {
    contrastSlider.value = 1; // Reset contrast to default value
    brightnessSlider.value = 0; // Reset brightness to default value
    updateUniforms();
  });

  contrastSlider.addEventListener("input", updateUniforms);
  brightnessSlider.addEventListener("input", updateUniforms);
}
