Part of the "images" array parameter passed to "pageSetup.js", contains <mark>**"colorscheme":**</mark> and then the following JSON.  I added formatting to make it clear:

```jsonld
{
    @type: "ColorScheme", 
    name: "Default Color Scheme", 
    colors: [
        {
            name: "Lymphocyte", 
            classid: 3, 
            color: "rgba(255, 0, 0, 255)"
        }, 
        {
            name: "Background", 
            classid: 4, 
            color: "rgba(0, 0, 255, 255)"
        }, 
        {
            name: "Tumor", 
            classid: 1, 
            color: "rgba(0, 255, 0, 255)"
        }, 
        {
            name: "Misc", 
            classid: 2, 
            color: "rgba(255, 255, 0, 255)"
        }
    ], 
    colorspectrum: [
        {
            color: "rgba(44, 131, 186, 255)", 
            high: 99, 
            low: 0
        }, 
        {
            color: "rgba(216, 63, 42, 255)", 
            high: 255, 
            low: 201
        }, 
        {
            color: "rgba(171, 221, 164, 255)", 
            high: 100, 
            low: 51
        }, 
        {
            color: "rgba(246, 173, 96, 255)", 
            high: 200, 
            low: 151
        }, 
        {
            color: "rgba(254, 251, 191, 255)", 
            high: 150, 
            low: 101
        }
    ], 
    @context: [
        {
            so: "https://schema.org/", 
            hal: "https://www.ebremer.com/halcyon/ns/", 
            name: {
                @id: "so:name"
            }, 
            classid: {
                @id: "hal:classid"
            }, 
            color: {
                @id: "hal:color"
            }, 
            colors: {
                @id: "hal:colors"
            }, 
            low: {
                @id: "hal:low"
            }, 
            high: {
                @id: "hal:high"
            }, 
            ColorByCertainty: {
                @id: "hal:ColorByCertainty"
            }, 
            ColorByClassID: {
                @id: "hal:ColorByClassID"
            }, 
            colorspectrum: {
                @id: "hal:colorspectrum"
            }, 
            ColorScheme: {
                @id: "hal:ColorScheme"
            }
        }
    ]
}
```

<mark>**"colors"**</mark> identifies the classes &ndash; Lymphocyte, Background, Tumor, etc.

<mark>**"colorspectrum"**</mark> identifies the probabilities

```jsonld
{
    color: "rgba(44, 131, 186, 255)", 
    high: 99, 
    low: 0
}
```

**ETC.**

`@context` just makes it JSON-LD.
