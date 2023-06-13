import { Talon } from "../libs/talon/talon.js";
//import { data } from "./data.js";

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
  xfilter: [],
  config: [],
  data: [],
};

const myTalon = new Talon(datasource);
