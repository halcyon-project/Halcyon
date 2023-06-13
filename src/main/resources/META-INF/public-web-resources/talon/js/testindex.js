import { Talon } from "../libs/talon/talon.js";
import { data } from "./data.js";

console.log("Staring Sniper Sight");

// Variables
const colorPalletes = {
  defaultColor: [
    "#1000ff",
    "#2055ff",
    "#408ea6",
    "#549f93",
    "#9faf90",
    "#e2b1b1",
    "#e2c2ff",
  ],
};

const helpers = {
  addFunction: (p, v) => {
    p++;
    return p;
  },
  removeFunction: (p, v) => {
    p--;
    return p;
  },
  initializeFunction: () => {
    return 0;
  },
};

const datasource = {
  d3: [],
  xfilter: [
    {
      id: 1,
      name: "accessDim",
      type: "dimension",
      dim_field: "access",
    },
    {
      id: 2,
      name: "accessGroup",
      type: "group",
      dimension: "accessDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 3,
      name: "speciesDim",
      type: "dimension",
      dim_field: "species",
    },
    {
      id: 4,
      name: "speciesGroup",
      type: "group",
      dimension: "speciesDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 5,
      name: "stainingDim",
      type: "dimension",
      dim_field: "protocol",
    },
    {
      id: 6,
      name: "stainingGroup",
      type: "group",
      dimension: "stainingDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 7,
      name: "dataFormatDim",
      type: "dimension",
      dim_field: "data_format",
    },
    {
      id: 8,
      name: "dataFormatGroup",
      type: "group",
      dimension: "dataFormatDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 9,
      name: "supportingDataDim",
      type: "dimension",
      dim_field: "supporting_data",
    },
    {
      id: 10,
      name: "supportingDataGroup",
      type: "group",
      dimension: "supportingDataDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 11,
      name: "cancerTypeDim",
      type: "dimension",
      dim_field: "cancer_type",
    },
    {
      id: 12,
      name: "cancerTypeGroup",
      type: "group",
      dimension: "cancerTypeDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 13,
      name: "cancerLocationDim",
      type: "dimension",
      dim_field: "location",
    },
    {
      id: 14,
      name: "cancerLocationGroup",
      type: "group",
      dimension: "cancerLocationDim",
      group_method: "reduce",
      add_function: helpers.addFunction,
      remove_function: helpers.removeFunction,
      initialize_function: helpers.initializeFunction,
    },
    {
      id: 15,
      name: "collectionDim",
      type: "dimension",
      dim_field: "collection",
    },
    {
      id: 16,
      name: "collectionGroup",
      type: "group",
      dimension: "collectionDim",
      group_method: "all",
    },
  ],
  config: [
    {
      id: 1,
      type: "pieChart",
      name: "accessPiechart",
      dom_id: "access-pie",
      grid_title: "Collection Access",
      grid_description:
        "Showing the number of Collections in Access Categories",
      grid_width: 2,
      grid_height: 2,
      dimension: "accessDim",
      group: "accessGroup",
      title_function: function (d) {
        return (
          d.key +
          ": " +
          dc.utils.printSingleValue((d.value / this.pieTotalCount) * 100) +
          "%"
        );
      },
    },
    {
      id: 2,
      type: "pieChart",
      name: "speciesPiechart",
      dom_id: "species-pie",
      grid_title: "Species",
      grid_description:
        "Showing the number of Collections in Access Categories",
      grid_width: 2,
      grid_height: 2,
      dimension: "speciesDim",
      group: "speciesGroup",
      title_function: function (d) {
        return (
          d.key +
          ": " +
          dc.utils.printSingleValue((d.value / this.pieTotalCount) * 100) +
          "%"
        );
      },
      ordinalColors: ["#462255", "#313b72", "#62a87c", "#7ee081", "#c3f3c0"],
    },
    {
      id: 3,
      type: "rowChart",
      name: "protocolRowChart",
      dom_id: "protocolRowChart",
      grid_title: "Staining Protocol",
      grid_description:
        "Showing the number of Collections in Staining Protocol",
      grid_width: 3,
      grid_height: 3,
      dimension: "stainingDim",
      group: "stainingGroup",
      margins: { top: 20, left: 10, right: 10, bottom: 20 },
      elasticX: true,
      label: (d) => {
        return d.key;
      },
      title: (d) => {
        return d.value;
      },
      // ordinalColors: [
      //   "#1000ff",
      //   "#2055ff",
      //   "#408ea6",
      //   "#549f93",
      //   "#9faf90",
      //   "#e2b1b1",
      //   "#e2c2ff",
      // ],
    },
    {
      id: 4,
      type: "barChart",
      name: "formatBarChart",
      dom_id: "formatBarChart",
      grid_title: "Data Format",
      grid_description: "Showing the number of Collections in each Data Format",
      grid_width: 4,
      grid_height: 3,
      dimension: "dataFormatDim",
      group: "dataFormatGroup",
      brushOn: true,
      x: {
        scale: {
          type: "band",
        },
      },
      xUnits: () => {
        return dc.units.ordinal;
      },
      barPadding: 0.1,
      // ordinalColors: [
      //   "#1000ff",
      //   "#2055ff",
      //   "#408ea6",
      //   "#549f93",
      //   "#9faf90",
      //   "#e2b1b1",
      //   "#e2c2ff",
      // ],
    },
    {
      id: 5,
      type: "rowChart",
      name: "supportingDataRowChart",
      dom_id: "supportingDataRowChart",
      grid_title: "Supporting Data",
      grid_description:
        "Showing the number of Collections in each type of supporting data",
      grid_width: 4,
      grid_height: 3,
      dimension: "supportingDataDim",
      group: "supportingDataGroup",
      margins: { top: 5, left: 5, right: 5, bottom: 5 },
      elasticX: true,
      label: (d) => {
        return d.key;
      },
      title: (d) => {
        return d.value;
      },
    },
    {
      id: 6,
      type: "rowChart",
      name: "cancerTypeRowChart",
      dom_id: "cancerTypeRowChart",
      grid_title: "Cancer Type",
      grid_description: "Showing the number of Collections in each cancer type",
      grid_width: 4,
      grid_height: 5,
      dimension: "cancerTypeDim",
      group: "cancerTypeGroup",
      margins: { top: 5, left: 5, right: 5, bottom: 5 },
      elasticX: true,
      label: (d) => {
        return d.key;
      },
      title: (d) => {
        return d.value;
      },
    },
    {
      id: 7,
      type: "rowChart",
      name: "cancerLocationRowChart",
      dom_id: "cancerLocationRowChart",
      grid_title: "Cancer Location",
      grid_description:
        "Showing the number of Collections in each cancer location",
      grid_width: 4,
      grid_height: 3,
      dimension: "cancerLocationDim",
      group: "cancerLocationGroup",
      margins: { top: 20, left: 10, right: 10, bottom: 20 },
      elasticX: true,
      label: (d) => {
        return d.key;
      },
      title: (d) => {
        return d.value;
      },
    },
    {
      id: 8,
      type: "dataTable",
      name: "collectionDataTable",
      dom_id: "collectionDataTable",
      dimension: "collectionDim",
      group: "collectionGroup",
      grid_title: "Collection Data Table",
      grid_description: "Showing all rows of Collections",
      grid_width: 12,
      grid_height: 6,
      buttons: ["csv", "pdf", "excel", "print"],
      autoWidth: true,
      paging: true,
      pagingSizeChange: true,
      responsive: true,
      showGroups: false,
      size: 25,
      search: true,
      order: "asc",
      sortBy: [["collection", "asc"]],
      columns: [
        {
          title: "Collection",
          data: "collection",
        },
        {
          title: "Visualize Data",
          data: "view",
        },
        {
          title: "Cancer Type",
          data: "cancer_type",
        },
        {
          title: "Location",
          data: "location",
        },
        {
          title: "Species",
          data: "species",
        },
        {
          title: "Data Format",
          data: "data_format",
        },
        {
          title: "Supporting Data",
          data: "supporting_data",
        },
        {
          title: "Protocol",
          data: "protocol",
        },
        {
          title: "Image Type",
          data: "modality",
        },
        {
          title: "Pixel Aspect Ratio",
          data: "par",
        },
        {
          title: "Magnification",
          data: "magnification",
        },
        {
          title: "Size (GB)",
          data: "size",
        },
        {
          title: "Access",
          data: "access",
        },
        {
          title: "Updated",
          data: "updated",
        },
      ],
    },
  ],
  data: data,
};

const myTalon = new Talon(datasource);
