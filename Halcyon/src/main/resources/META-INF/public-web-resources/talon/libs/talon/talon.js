/**
 * @name datatalon.js
 * @fileOverview Library to create graphs and tables from data retrieved from the server.
 * @author <a href="mailto:joseph.balsamo@stonybrook.edu">Joseph Balsamo</a>
 * @version 0.9
 * @created 2020-03-01
 * @todo
 */

const scprint_filter = (filter) => {
  let f = eval(filter);
  if (typeof f.length != "undefined") {
  } else {
  }
  if (typeof f.top != "undefined") {
    f = f.top(Infinity);
  } else {
  }
  if (typeof f.dimension != "undefined") {
    f = f
      .dimension(function (d) {
        return "";
      })
      .top(Infinity);
  } else {
  }
  console.log(
    filter +
      "(" +
      f.length +
      ") = " +
      JSON.stringify(f)
        .replace("[", "[\n\t")
        .replace(/}\,/g, "},\n\t")
        .replace("]", "\n]")
  );
};

class Talon {
  d3Values = [];
  xfValues = [];
  graphs = [];
  tables = [];
  ndx = null;
  gridstack = null;
  gs_cell_size = 0;

  constructor(datasource) {
    let gs_options = {
      dragable: {
        scroll: false,
      },
      margin: 15,
    };
    this.d3 = datasource.d3;
    this.xfilter = datasource.xfilter;
    this.config = datasource.config;
    this.data = datasource.data;
    this.count = this.data.length;
    this.gridstack = GridStack.init(gs_options);
    this.grid_items = [];
    this.gs_cell_size = this.gridstack.getCellHeight();
    this.gridstack.on("resizestop", (e, i) => {
      this.gs_cell_size = this.gridstack.getCellHeight();
      dc.renderAll();
    });
    this.init();
  }

  init() {
    this.ndx = crossfilter(this.data);
    this.all = this.ndx.groupAll();
    this.tsDim = this.ndx.dimension((d) => {
      let result = "";
      Object.values(d).forEach((item) => {
        result += item + " ";
      });
      return result.toLowerCase();
    });
    this.textSearch = new dc.TextFilterWidget("#text-search")
      .dimension(this.tsDim)
      .placeHolder("Search")
      .normalize(function (s) {
        return s.toLowerCase();
      });
    this.dataCount = new dc.DataCount("#dc-counter");
    this.dataCount.crossfilter(this.ndx).groupAll(this.all).html({
      some: "%filter-count / %total-count",
      all: "%filter-count / %total-count",
    });
    //Setup the dashboard
    this.config.forEach((d) => {
      if (d.dom_id != undefined) {
        let content = `
          <div class="top-bar">
            <div class="top-title">
              ${d.grid_title}
            </div>
            <div class="tooltip">
              <span class="tooltiptext">${d.grid_description}</span>
              <div class="top-icons">
                <span class="material-symbols-outlined">
                  info
                </span>
              </div>
            </div>
          </div>
          <div id="${d.dom_id}"></div>
        `;
        this.grid_items[d.id] = this.gridstack.addWidget({
          id: d.dom_id,
          w: d.grid_width,
          h: d.grid_height,
          content: content,
        });
      }
    });
    // Process d3 values
    this.d3.forEach((d) => {
      if (d.type == "parsedate") {
        const parseDate = d3.timeParse(d.format);
        this.data.forEach((e) => {
          e[d.fields[0]] = parseDate(e[d.fields[0]]);
        });
      }
      if (d.type == "sum") {
        this.data.forEach((e) => {
          e[d.name] = 0;
          d.fields.forEach((f) => {
            e[d.name] += e[f];
          });
        });
      }
      if (d.type == "getFullYear") {
        this.data.forEach((e) => {
          e[d.name] = e[d.fields[0]].getFullYear();
        });
      }
    });

    // Process xfilter values
    this.xfilter.forEach((d) => {
      if (d.type == "dimension") {
        if (d.dim_function != undefined) {
          this.xfValues[d.name] = this.ndx.dimension((e) =>
            d.custom_function(e)
          );
        } else {
          this.xfValues[d.name] = this.ndx.dimension((e) => {
            return e[d.dim_field];
          });
        }
      }

      if (d.type == "group") {
        if (d.group_method == "reduceSum") {
          if (d.field_function == "return") {
            this.xfValues[d.name] = this.xfValues[d.dimension]
              .group()
              .reduceSum((e) => e[d.group_field]);
          } else if (d.field_function == "pluck") {
            this.xfValues[d.name] = this.xfValues[d.dimension]
              .group()
              .reduceSum(dc.pluck(d.group_field));
          } else {
            this.xfValues[d.name] = this.xfValues[d.dimension]
              .group()
              .reduceSum(d.custom_function);
          }
        } else if (d.group_method == "reduce") {
          this.xfValues[d.name] = this.xfValues[d.dimension]
            .group()
            .reduce(d.add_function, d.remove_function, d.initialize_function);
        } else if (d.group_method == "all") {
          this.xfValues[d.name] = this.xfValues[d.dimension].group((d) => {
            return "";
          });
        } else {
          this.xfValues[d.name] = this.xfValues[d.dimension].group();
        }
      }

      if (d.type == "bottom") {
        this.xfValues[d.name] =
          this.xfValues[d.dimension].bottom(1)[0][d.field];
      }

      if (d.type == "top") {
        this.xfValues[d.name] = this.xfValues[d.dimension].top(1)[0][d.field];
      }

      if (d.type == "constant") {
        this.xfValues[d.name] = d.value;
      }
    });

    //Process graphs
    this.config.forEach((conf) => {
      // Bar Chart
      if (conf.type == "barChart") {
        let lid = this.graphs.push(dc.barChart("#" + conf.dom_id)) - 1;

        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width * 0.95);
          this.graphs[lid].width(conf.w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height);
          this.graphs[lid].height(conf.height);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.renderArea != undefined) {
          this.graphs[lid].renderArea(conf.renderArea);
        }
        if (conf.brushOn != undefined) {
          this.graphs[lid].brushOn(conf.brushOn);
        }
        if (conf.group != undefined) {
          if (conf.group.field != undefined) {
            this.graphs[lid].group(
              this.xfValues[conf.group.field],
              conf.group.label
            );
          } else {
            this.graphs[lid].group(this.xfValues[conf.group]);
          }
        }
        if (conf.x != undefined) {
          if (conf.x.scale != undefined) {
            switch (conf.x.scale.type) {
              case "timescale":
                if (conf.x.scale.d3 != undefined) {
                  this.graphs[lid].x(
                    d3
                      .scaleTime()
                      .domain([
                        this.xfValues["minDate"],
                        this.xfValues["maxDate"],
                      ])
                  );
                }
                break;
              case "linear":
                if (conf.x.scale.domain != undefined) {
                  this.graphs[lid].x(
                    d3
                      .scaleLinear()
                      .domain([
                        this.xfValues["minPercent"],
                        this.xfValues["maxPercent"],
                      ])
                  );
                }
                break;
              case "ordinal":
                let domain = this.xfValues[conf.group].all().map((d) => d.key);
                if (conf.x.scale.domain != undefined) {
                  this.graphs[lid].x(d3.scaleOrdinal().domain(domain));
                }
                break;
              case "band":
                this.graphs[lid].x(d3.scaleBand());
                break;
              default:
            }
          }
        }
        if (conf.barPadding != undefined) {
          this.graphs[lid].barPadding(conf.barPadding);
        }
        if (conf.yAxisLabel != undefined) {
          this.graphs[lid].yAxisLabel(conf.yAxisLabel);
        }
        if (conf.xAxisLabel != undefined) {
          this.graphs[lid].xAxisLabel(conf.xAxisLabel);
        }
        if (conf.xUnits != undefined) {
          this.graphs[lid].xUnits(conf.xUnits());
        }
        if (conf.ordinalColors != undefined) {
          this.graphs[lid].ordinalColors(conf.ordinalColors);
        }
        if (conf.legend != undefined) {
          this.graphs[lid].legend(
            dc
              .legend()
              .x(conf.legend.x)
              .y(conf.legend.y)
              .itemHeight(conf.legend.itemHeight)
              .gap(conf.legend.gap)
          );
        }
        this.graphs[lid].turnOnControls(true);
        // Event Handlers
        this.graphs[lid].on("preRender", (chart) => {
          let anchor = chart._anchor.slice(1);
          let query = "[gs-id=" + anchor + "]";
          let el = document.querySelector(query);
          let gw = el.getAttribute("gs-w");
          let gh = el.getAttribute("gs-h");
          let h = Math.floor(this.gs_cell_size * gh * 0.9);
          let w = Math.floor(this.gs_cell_size * gw * 0.9);
          chart.width(w);
          chart.height(h);
        });

        let xf = this.xfValues;
        this.graphs[lid].on("filtered.monitor", function (chart, filter) {
          dc.renderAll();
        });
      }
      // Row Chart
      if (conf.type == "rowChart") {
        let lid = this.graphs.push(new dc.RowChart("#" + conf.dom_id)) - 1;
        // Process the configurations.
        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width);
          this.graphs[lid].width(conf.w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height * 0.94);
          this.graphs[lid].height(conf.height);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.renderArea != undefined) {
          this.graphs[lid].renderArea(conf.renderArea);
        }
        if (conf.brushOn != undefined) {
          this.graphs[lid].brushOn(conf.brushOn);
        }
        if (conf.group != undefined) {
          if (conf.group.field != undefined) {
            this.graphs[lid].group(
              this.xfValues[conf.group.field],
              conf.group.label
            );
          } else {
            this.graphs[lid].group(this.xfValues[conf.group]);
          }
        }
        if (conf.x != undefined) {
          if (conf.x.scale != undefined) {
            switch (conf.x.scale.type) {
              case "timescale":
                if (conf.x.scale.d3 != undefined) {
                  this.graphs[lid].x(
                    d3
                      .scaleTime()
                      .domain([
                        this.xfValues["minDate"],
                        this.xfValues["maxDate"],
                      ])
                  );
                }
                break;
              case "linear":
                if (conf.x.scale.domain != undefined) {
                  this.graphs[lid].x(
                    d3
                      .scaleLinear()
                      .domain([
                        this.xfValues["minPercent"],
                        this.xfValues["maxPercent"],
                      ])
                  );
                }
                break;
              case "ordinal":
                let domain = this.xfValues[conf.group].all().map((d) => d.key);
                if (conf.x.scale.domain != undefined) {
                  this.graphs[lid]
                    .x(d3.scaleOrdinal().domain(domain))
                    .label(domain);
                }
                break;
              default:
            }
          }
        }
        if (conf.yAxisLabel != undefined) {
          this.graphs[lid].yAxisLabel(conf.yAxisLabel);
        }
        if (conf.xAxisLabel != undefined) {
          this.graphs[lid].xAxisLabel(conf.xAxisLabel);
        }
        // if (conf.xUnits != undefined) {
        //   this.graphs[lid].xUnits(() => conf.xUnits);
        // }
        // if (conf.legend != undefined) {
        //   this.graphs[lid].legend(
        //     dc
        //       .legend()
        //       .x(conf.legend.x)
        //       .y(conf.legend.y)
        //       .itemHeight(conf.legend.itemHeight)
        //       .gap(conf.legend.gap)
        //   );
        // }
        if (conf.elasticX != undefined) {
          this.graphs[lid].elasticX(conf.elasticX);
        }
        if (conf.elasticY != undefined) {
          this.graphs[lid].elasticY(conf.elasticY);
        }
        if (conf.xAxis != undefined) {
          if (conf.xAxis.ticks != undefined) {
            this.graphs[lid].xAxis().ticks(conf.xAxis.ticks);
          }
        }
        if (conf.ordinalColors != undefined) {
          this.graphs[lid].ordinalColors(conf.ordinalColors);
        }
        this.graphs[lid].title((d) => d.value);
        this.graphs[lid].label((d) => d.key);
        // this.graphs[lid].turnOnControls(true);
        // Event Handlers
        this.graphs[lid].on("preRender", (chart) => {
          let anchor = chart._anchor.slice(1);
          let query = "[gs-id=" + anchor + "]";
          let el = document.querySelector(query);
          let gw = el.getAttribute("gs-w");
          let gh = el.getAttribute("gs-h");
          let h = Math.floor(this.gs_cell_size * gh * 0.9);
          let w = Math.floor(this.gs_cell_size * gw * 0.9);
          chart.width(w);
          chart.height(h);
        });

        let xf = this.xfValues;
        this.graphs[lid].on("filtered.monitor", function (chart, filter) {
          dc.renderAll();
        });
      }
      // Line Chart
      if (conf.type == "lineChart") {
        let lid = this.graphs.push(dc.lineChart(conf.dom_id)) - 1;

        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width * 0.9);
          this.graphs[lid].width(conf.w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height);
          this.graphs[lid].height(conf.height);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.renderArea != undefined) {
          this.graphs[lid].renderArea(conf.renderArea);
        }
        if (conf.brushOn != undefined) {
          this.graphs[lid].brushOn(conf.brushOn);
        }
        if (conf.group != undefined) {
          this.graphs[lid].group(
            this.xfValues[conf.group.field],
            conf.group.label
          );
        }
        if (conf.stack != undefined) {
          conf.stack.forEach((s) => {
            this.graphs[lid].stack(this.xfValues[s.field], s.label);
          });
        }
        if (conf.x != undefined) {
          if (conf.x.scale != undefined) {
            if (conf.x.scale.d3 != undefined) {
              this.graphs[lid].x(
                d3
                  .scaleTime()
                  .domain([this.xfValues["minDate"], this.xfValues["maxDate"]])
              );
              this.graphs[lid].xUnits(d3.timeDays);
            }
          }
        }
        if (conf.yAxisLabel != undefined) {
          this.graphs[lid].yAxisLabel(conf.yAxisLabel);
        }
        if (conf.xAxisLabel != undefined) {
          this.graphs[lid].xAxisLabel(conf.xAxisLabel);
        }
        if (conf.legend != undefined) {
          this.graphs[lid].legend(
            dc
              .legend()
              .x(conf.legend.x)
              .y(conf.legend.y)
              .itemHeight(conf.legend.itemHeight)
              .gap(conf.legend.gap)
          );
        }
        this.graphs[lid].turnOnControls(true);

        // Event Handlers
        this.graphs[lid].on("preRender", (chart) => {
          let anchor = chart._anchor.slice(1);
          let query = "[gs-id=" + anchor + "]";
          let el = document.querySelector(query);
          let gw = el.getAttribute("gs-w");
          let gh = el.getAttribute("gs-h");
          let h = Math.floor(this.gs_cell_size * gh * 0.9);
          let w = Math.floor(this.gs_cell_size * gw * 0.9);
          chart.width(w);
          chart.height(h);
        });
      }
      // Scatter plot Chart
      if (conf.type == "scatterplot") {
        let lid = this.graphs.push(dc.scatterPlot("#" + conf.dom_id)) - 1;

        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width * 0.94);
          this.graphs[lid].width(conf.w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height);
          this.graphs[lid].height(conf.height);
        }
        if (conf.symbolSize != undefined) {
          this.graphs[lid].symbolSize(conf.symbolSize);
        }
        if (conf.clipPadding != undefined) {
          this.graphs[lid].clipPadding(conf.clipPadding);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.renderArea != undefined) {
          this.graphs[lid].renderArea(conf.renderArea);
        }
        if (conf.brushOn != undefined) {
          this.graphs[lid].brushOn(conf.brushOn);
        }
        if (conf.group != undefined) {
          this.graphs[lid].group(
            this.xfValues[conf.group.field],
            conf.group.label
          );
        }
        if (conf.x != undefined) {
          if (conf.x.scale != undefined) {
            if (conf.x.scale.d3 != undefined) {
              if (conf.x.scale.d3.type != undefined) {
                {
                  if (conf.x.scale.d3.type == "scaleTime") {
                    this.graphs[lid].x(
                      d3
                        .scaleTime()
                        .domain([
                          this.xfValues["minDate"],
                          this.xfValues["maxDate"],
                        ])
                    );
                  } else if (conf.x.scale.d3.type == "scaleLinear") {
                    this.graphs[lid].x(
                      d3
                        .scaleLinear()
                        .domain([
                          this.xfValues["minPercent"],
                          this.xfValues["maxPercent"],
                        ])
                    );
                  }
                }
              }
            }
          }
        }
        if (conf.yAxisLabel != undefined) {
          this.graphs[lid].yAxisLabel(conf.yAxisLabel);
        }
        if (conf.xAxisLabel != undefined) {
          this.graphs[lid].xAxisLabel(conf.xAxisLabel);
        }
        if (conf.legend != undefined) {
          this.graphs[lid].legend(
            dc
              .legend()
              .x(conf.legend.x)
              .y(conf.legend.y)
              .itemHeight(conf.legend.itemHeight)
              .gap(conf.legend.gap)
          );
        }
        this.graphs[lid].turnOnControls(true);
        this.graphs[lid].on("preRender", (chart) => {
          let anchor = chart._anchor.slice(1);
          let query = "[gs-id=" + anchor + "]";
          let el = document.querySelector(query);
          let gw = el.getAttribute("gs-w");
          let gh = el.getAttribute("gs-h");
          let h = Math.floor(this.gs_cell_size * gh * 0.9);
          let w = Math.floor(this.gs_cell_size * gw * 0.9);
          chart.width(w);
          chart.height(h);
        });
        // Event Handlers for testing
        let xf = this.xfValues;
        this.graphs[lid].on("filtered.monitor", function (chart, filter) {
          dc.renderAll();
        });
      }
      // Pie Chart
      if (conf.type == "pieChart") {
        let lid = this.graphs.push(dc.pieChart("#" + conf.dom_id)) - 1;
        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width * 0.95);
          this.graphs[lid].width(w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height * 0.95);
          this.graphs[lid].height(h);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.group != undefined) {
          if (conf.group_field != undefined) {
            this.graphs[lid].group(this.xfValues[conf.group.field]);
          } else {
            this.graphs[lid].group(this.xfValues[conf.group]);
          }
        }
        if (conf.innerRadius != undefined) {
          this.graphs[lid].innerRadius(conf.innerRadius);
        }
        if (conf.radius != undefined) {
          this.graphs[lid].radius(conf.radius);
        } else {
          let r = Math.floor(this.gs_cell_width * conf.grid_width);
          this.graphs[lid].radius(r);
        }
        //Event Handlers
        this.graphs[lid].on("preRender", (chart) => {
          let anchor = chart._anchor.slice(1);
          let query = "[gs-id=" + anchor + "]";
          let el = document.querySelector(query);
          let gw = el.getAttribute("gs-w");
          let gh = el.getAttribute("gs-h");
          let h = Math.floor(this.gs_cell_size * gh * 0.75);
          let w = Math.floor(this.gs_cell_size * gw * 0.75);
          let r = Math.floor(this.gs_cell_width * gw * 0.4);
          chart.width(w);
          chart.height(h);
          chart.radius(r);
          let pieNumber = {
            pieTotalCount: this.xfValues[conf.dimension].groupAll().value(),
          };
          console.log(
            "🚀 ~ file: scythe.js:687 ~ Scythe ~ this.config.forEach ~ pieNumber.pieTotalCount:",
            pieNumber.pieTotalCount
          );
          let boundTitleFunc = conf.title_function.bind(pieNumber);
          g.title(boundTitleFunc);
        });
        let g = this.graphs[lid];
        let pieNumber = {
          pieTotalCount: this.xfValues[conf.dimension].groupAll().value(),
        };
        console.log(
          "🚀 ~ file: scythe.js:687 ~ Scythe ~ this.config.forEach ~ pieNumber.pieTotalCount:",
          pieNumber.pieTotalCount
        );
        if (conf.title_function != undefined) {
          let boundTitleFunc = conf.title_function.bind(pieNumber);
          g.title(boundTitleFunc);
        }
      }
      // DataCount
      // if (conf.type == "dataCount") {
      //   scprint_filter(this.grpAll);
      //   let lid = this.graphs.push(dc.dataCount("#" + conf.dom_id)) - 1;
      //   if (conf.html != undefined) {
      //     this.graphs[lid]
      //       .crossfilter(this.ndx)
      //       .groupAll(this.ndx.groupAll)
      //       .html(conf.html);
      //   }
      // }
      // Data Table
      if (conf.type == "dataTable") {
        let did = "#" + conf.dom_id;
        let lid = this.graphs.push(dc.tableview(did)) - 1;

        if (conf.size !== undefined) {
          this.graphs[lid].size(conf.size);
        }
        if (conf.autoWidth !== undefined) {
          this.graphs[lid].enableAutoWidth(conf.autoWidth);
        }
        if (conf.paging !== undefined) {
          this.graphs[lid].enablePaging(conf.paging);
        }
        if (conf.pagingSizeChange !== undefined) {
          this.graphs[lid].enablePagingSizeChange(conf.pagingSizeChange);
        }
        if (conf.responsive !== undefined) {
          this.graphs[lid].responsive(conf.responsive);
        }
        if (conf.width != undefined) {
          this.graphs[lid].width(conf.width);
        } else {
          let w = Math.floor(this.gs_cell_size * conf.grid_width * 0.8);
          this.graphs[lid].width(w);
        }
        if (conf.height != undefined) {
          this.graphs[lid].height(conf.height);
        } else {
          let h = Math.floor(this.gs_cell_size * conf.grid_height * 0.8);
          this.graphs[lid].height(h);
        }
        if (conf.dimension != undefined) {
          this.graphs[lid].dimension(this.xfValues[conf.dimension]);
        }
        if (conf.group != undefined) {
          this.graphs[lid].group(this.xfValues[conf.group]);
        }
        if (conf.showGroups != undefined) {
          this.graphs[lid].showGroups(conf.showGroup);
        }
        if (conf.buttons != undefined) {
          this.graphs[lid].buttons(conf.buttons);
        }
        if (conf.search != undefined) {
          this.graphs[lid].enableSearch(conf.search);
        }
        if (conf.columns != undefined) {
          this.graphs[lid].columns(conf.columns);
        }
        if (conf.order != undefined) {
          this.graphs[lid].order(conf.order);
        }
        this.graphs[lid].sortBy(conf.sortBy);
      }
    });
    dc.renderAll();
  }

  return_data() {
    return this.data;
  }

  return_graphs() {
    return this.graphs;
  }

  return_xfValues() {
    return this.xfValues;
  }
}

export { Talon, scprint_filter };
