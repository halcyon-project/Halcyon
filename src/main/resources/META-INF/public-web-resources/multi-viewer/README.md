# multi-viewer

Multiple synchronized OpenSeadragon viewers

Renders colorized segmentations, heatmaps, etc.

The code uses HTML5, JavaScript ES6, CSS3, the npm package manager, Grunt automation...

<!-- Segmentation layer color ordering:<br>
![](images/color-ordering.png) -->

## Install & Build

OS X & Linux:

```sh
npm install; grunt
```

## Generate docs

```sh
npm run doc
```

## Usage

Explore & run the HTML files for example usage.

## Meta

Tammy DiPrima tammy.diprima&#64;stonybrook.edu

Distributed under the BSD 3-Clause License. See [LICENSE.txt](LICENSE.txt) for more information.

## Contributing

When contributing, please attempt to match the code style already in the codebase.

1. Fork this repo via GitHub
2. Create your feature branch

```sh
git checkout -b feature/fooBar
```

3. Commit your changes

```sh
git commit -m "Add fooBar"
```

4. Push to the branch

```sh
git push origin feature/fooBar
```

5. Create a new Pull Request via GitHub

## Linting code

**Lint JavaScript**

```sh
# all
npm run lint:write
# or
npx eslint yourscript.js --fix
```

**Lint CSS**

```sh
csscomb file.css
```

## Dependencies

[color-picker](https://github.com/taufik-nurrohman/color-picker/releases/tag/v2.2.4)

[fabric.js](https://github.com/fabricjs/fabric.js/releases/tag/v521)

[Font Awesome](https://use.fontawesome.com/releases/v5.15.3/fontawesome-free-5.15.3-web.zip)

[jQuery](https://github.com/jquery/jquery/archive/refs/tags/3.5.1.tar.gz)


[OpenSeadragon](https://github.com/openseadragon/openseadragon/releases/tag/v2.4.2)

[OpenseadragonFabricjsOverlay](https://github.com/tdiprima/OpenseadragonFabricjsOverlay)

[OpenSeadragonFiltering](https://github.com/usnistgov/OpenSeadragonFiltering)

[OpenSeadragonScalebar
](https://github.com/usnistgov/OpenSeadragonScalebar)
