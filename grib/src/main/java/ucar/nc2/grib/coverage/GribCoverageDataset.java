/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2.grib.coverage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jcip.annotations.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.featurecollection.FeatureCollectionConfig;
import thredds.inventory.CollectionUpdateType;
import ucar.coord.Coordinate;
import ucar.coord.CoordinateEns;
import ucar.coord.CoordinateRuntime;
import ucar.coord.CoordinateTime;
import ucar.coord.CoordinateTime2D;
import ucar.coord.CoordinateTimeAbstract;
import ucar.coord.CoordinateTimeIntv;
import ucar.coord.CoordinateVert;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.ma2.RangeIterator;
import ucar.ma2.SectionIterable;
import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainerHelper;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.ft2.coverage.CoordAxisReader;
import ucar.nc2.ft2.coverage.CoordsSet;
import ucar.nc2.ft2.coverage.Coverage;
import ucar.nc2.ft2.coverage.CoverageCoordAxis;
import ucar.nc2.ft2.coverage.CoverageCoordAxis1D;
import ucar.nc2.ft2.coverage.CoverageCoordAxisBuilder;
import ucar.nc2.ft2.coverage.CoverageCoordSys;
import ucar.nc2.ft2.coverage.CoverageDataset;
import ucar.nc2.ft2.coverage.CoverageDatasetCollection;
import ucar.nc2.ft2.coverage.CoverageReader;
import ucar.nc2.ft2.coverage.CoverageTransform;
import ucar.nc2.ft2.coverage.GeoReferencedArray;
import ucar.nc2.ft2.coverage.LatLonAxis2D;
import ucar.nc2.ft2.coverage.SubsetParams;
import ucar.nc2.ft2.coverage.TimeOffsetAxis;
import ucar.nc2.grib.EnsCoord;
import ucar.nc2.grib.GdsHorizCoordSys;
import ucar.nc2.grib.TimeCoord;
import ucar.nc2.grib.VertCoord;
import ucar.nc2.grib.collection.GribCdmIndex;
import ucar.nc2.grib.collection.GribCollectionImmutable;
import ucar.nc2.grib.collection.GribDataReader;
import ucar.nc2.grib.collection.GribIosp;
import ucar.nc2.grib.grib2.Grib2Utils;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.units.SimpleUnit;
import ucar.nc2.util.Counters;
import ucar.nc2.util.Misc;
import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.util.Parameter;

/**
 * Create a CoverageDataset from a GribCollection file
 *
 * @author John
 * @since 8/1/2015
 */
@Immutable
public class GribCoverageDataset implements CoverageReader, CoordAxisReader {
  static private final Logger logger = LoggerFactory.getLogger(GribCoverageDataset.class);
  static private final boolean show = false;

  static public CoverageDatasetCollection open(String endpoint) throws IOException {
    GribCollectionImmutable gc;

    // try to fail fast
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile(endpoint, "r");
      GribCdmIndex.GribCollectionType type = GribCdmIndex.getType(raf);
      boolean isIndexFile = (type != GribCdmIndex.GribCollectionType.none);
      if (isIndexFile) {
        gc = GribCdmIndex.openGribCollectionFromIndexFile(raf, new FeatureCollectionConfig(), logger);  // LOOK no config !
      } else {
        gc = GribCdmIndex.openGribCollectionFromRaf(raf, new FeatureCollectionConfig(), CollectionUpdateType.nocheck, logger);
      }

      if (gc == null) {
        raf.close();
        return null;
      }

    } catch (IOException ioe) {
      if (raf != null) raf.close();
      return null;
    }

    List<CoverageDataset> datasets = new ArrayList<>();
    for (GribCollectionImmutable.Dataset ds : gc.getDatasets()) {
      for (GribCollectionImmutable.GroupGC group : ds.getGroups()) {
        GribCoverageDataset gribCov = new GribCoverageDataset(gc, ds, group);
        datasets.add(gribCov.makeCoverageDataset());
      }
    }
    return new CoverageDatasetCollection(gc, datasets);
  }

  //////////////////////////////////////////////////////////////////

  private final GribCollectionImmutable gribCollection;
  private final GribCollectionImmutable.Dataset ds;
  private final GribCollectionImmutable.GroupGC group;
  private final CoverageCoordSys.Type coverageType;
  private final boolean isGrib1, isLatLon, isCurvilinearOrthogonal;

  public GribCoverageDataset(GribCollectionImmutable gribCollection, GribCollectionImmutable.Dataset ds, GribCollectionImmutable.GroupGC group) {
    this.gribCollection = gribCollection;
    this.ds = (ds != null) ? ds : gribCollection.getDataset(0);
    this.group = (group != null) ? group : this.ds.getGroup(0);
    this.isGrib1 = gribCollection.isGrib1;

    GdsHorizCoordSys hcs = this.group.getGdsHorizCoordSys();
    this.isLatLon = hcs.isLatLon(); // isGrib1 ? hcs.isLatLon() : Grib2Utils.isLatLon(hcs.template, gribCollection.getCenter());
    this.isCurvilinearOrthogonal = !isGrib1 && Grib2Utils.isCurvilinearOrthogonal(hcs.template, gribCollection.getCenter());

    // figure out coverageType
    CoverageCoordSys.Type ct;
    switch (this.ds.getType()) {
      case MRC:
      case MRSTC:
      case TP:
      case TwoD:
        ct = CoverageCoordSys.Type.Fmrc;
        break;
      default:
        ct = CoverageCoordSys.Type.Grid;
    }
    if (isCurvilinearOrthogonal)
      ct = CoverageCoordSys.Type.Curvilinear;
    this.coverageType = ct;
  }

  @Override
  public void close() throws IOException {
    gribCollection.close();
  }

  @Override
  public String getLocation() {
    return gribCollection.getLocation()+"#"+group.getId(); // ??
  }

  /*
    SRC,               // GC: Single Runtime Collection                [ntimes]
    MRC,               // GC: Multiple Runtime Collection              [nruns, ntimes]
    MRSTC,             // GC: Multiple Runtime Single Time Collection  [nruns, 1]
    TP,                // PC: Multiple Runtime Single Time Partition   [nruns, 1]
    TwoD,              // PC: TwoD time partition                      [nruns, ntimes]
    Best,              // PC: Best time partition                      [ntimes]
      -- LOOK must generate aux runtime
   */

  public CoverageDataset makeCoverageDataset() {
    String name = gribCollection.getName() + "#" + ds.getType();
    if (ds.getGroupsSize() > 1)
      name += "-" + group.getId();

    AttributeContainerHelper gatts = new AttributeContainerHelper(name);
    gatts.addAll(gribCollection.getGlobalAttributes());

    // make horiz transform if needed
    List<CoverageTransform> transforms = new ArrayList<>();
    if (!isLatLon) {
      AttributeContainerHelper projAtts = new AttributeContainerHelper(group.horizCoordSys.getId());
      for (Parameter p : group.getGdsHorizCoordSys().proj.getProjectionParameters())
        projAtts.addAttribute(new Attribute(p));
      CoverageTransform projTransform = new CoverageTransform(group.horizCoordSys.getId(), projAtts, true);
      transforms.add(projTransform);
    }

    // potential variables - need to remove any 2D LatLon
    List<GribCollectionImmutable.VariableIndex> vars = new ArrayList<>(group.getVariables());

    List<CoverageCoordAxis> axes = new ArrayList<>();
    if (isCurvilinearOrthogonal)
      axes.addAll(makeHorizCoordinates2D(vars));
    else
      axes.addAll(makeHorizCoordinates());

    for (Coordinate axis : group.getCoordinates()) {
      switch (axis.getType()) {
        case runtime:
          addRuntimeCoordAxis((CoordinateRuntime) axis);
          break;
        case time2D:
          addTime2DCoordAxis((CoordinateTime2D) axis);
          break;
        case time:
        case timeIntv:
          addTimeCoordinates((CoordinateTimeAbstract) axis, axes);
          break;
        case vert:
          axes.add(makeCoordAxis((CoordinateVert) axis));
          break;
        case ens:
          axes.add(makeCoordAxis((CoordinateEns) axis));
          break;
      }
    }
    makeRuntimeCoordAxes(axes);
    makeTime2DCoordAxis(axes);

    Map<String, CoverageCoordSys> coordSysSet = new HashMap<>();
    for (GribCollectionImmutable.VariableIndex v : vars) {
      CoverageCoordSys sys = makeCoordSys(v, transforms);
      coordSysSet.put(sys.getName(), sys);                    // duplicates get eliminated here
    }
    List<CoverageCoordSys> coordSys = new ArrayList<>(coordSysSet.values());

    // all vars that are left are coverages
    List<Coverage> pgrids = vars.stream().map(this::makeCoverage).collect(Collectors.toList());
    GdsHorizCoordSys hcs = group.getGdsHorizCoordSys();
    return new CoverageDataset(name, coverageType, gatts, hcs.getLatLonBB(), hcs.getProjectionBB(), getCalendarDateRange(),
            coordSys, transforms, axes, pgrids, this);
  }

  CalendarDateRange dateRange;

  private void trackDateRange(CalendarDateRange cdr) {
    if (dateRange == null) dateRange = cdr;
    else dateRange = dateRange.extend(cdr);
  }

  CalendarDateRange getCalendarDateRange() {
    return dateRange;
  }

  private List<CoverageCoordAxis> makeHorizCoordinates() {
    GdsHorizCoordSys hcs = group.getGdsHorizCoordSys();

    List<CoverageCoordAxis> result = new ArrayList<>(2);
    if (isLatLon) {
      AttributeContainerHelper atts = new AttributeContainerHelper(CF.LATITUDE);
      atts.addAttribute(new Attribute(CDM.UNITS, CDM.LAT_UNITS));
      result.add(new CoverageCoordAxis1D( new CoverageCoordAxisBuilder(CF.LATITUDE, CDM.LAT_UNITS, null, DataType.FLOAT, AxisType.Lat, atts,
              CoverageCoordAxis.DependenceType.independent, null, CoverageCoordAxis.Spacing.regular,
              hcs.ny, hcs.getStartY(), hcs.getEndY(), hcs.dy, null, this, false)));

      atts = new AttributeContainerHelper(CF.LONGITUDE);
      atts.addAttribute(new Attribute(CDM.UNITS, CDM.LON_UNITS));
      result.add(new CoverageCoordAxis1D( new CoverageCoordAxisBuilder(CF.LONGITUDE, CDM.LON_UNITS, null, DataType.FLOAT, AxisType.Lon, atts, CoverageCoordAxis.DependenceType.independent,
              null, CoverageCoordAxis.Spacing.regular, hcs.nx, hcs.getStartX(), hcs.getEndX(), hcs.dx, null, this, false)));

    } else {
      AttributeContainerHelper atts = new AttributeContainerHelper("y");
      atts.addAttribute(new Attribute(CDM.UNITS, "km"));
      result.add(new CoverageCoordAxis1D( new CoverageCoordAxisBuilder("y", "km", CF.PROJECTION_Y_COORDINATE, DataType.FLOAT, AxisType.GeoY, atts, CoverageCoordAxis.DependenceType.independent,
              null, CoverageCoordAxis.Spacing.regular, hcs.ny, hcs.getStartY(), hcs.getEndY(), hcs.dy, null, this, false)));

      atts = new AttributeContainerHelper("x");
      atts.addAttribute(new Attribute(CDM.UNITS, "km"));
      result.add(new CoverageCoordAxis1D(new CoverageCoordAxisBuilder("x", "km", CF.PROJECTION_X_COORDINATE, DataType.FLOAT, AxisType.GeoX, atts, CoverageCoordAxis.DependenceType.independent,
              null, CoverageCoordAxis.Spacing.regular, hcs.nx, hcs.getStartX(), hcs.getEndX(), hcs.dx, null, this, false)));
    }
    return result;
  }

  /**
   * identify any variables that are really 2D lat/lon
   *
   * @param vars check this list, but remove lat/lon coordinates from it
   * @return lat/lon coordinates
   */
  private List<CoverageCoordAxis> makeHorizCoordinates2D(List<GribCollectionImmutable.VariableIndex> vars) {
    GdsHorizCoordSys hcs = group.getGdsHorizCoordSys();

    List<GribCollectionImmutable.VariableIndex> remove = new ArrayList<>();
    List<CoverageCoordAxis> result = new ArrayList<>();
    for (GribCollectionImmutable.VariableIndex vindex : vars) {
      Grib2Utils.LatLon2DCoord ll2d = Grib2Utils.getLatLon2DcoordType(vindex.getDiscipline(), vindex.getCategory(), vindex.getParameter());
      if (ll2d == null) continue;

      AxisType axisType = ll2d.getAxisType();
      String name = ll2d.toString();
      AttributeContainerHelper atts = new AttributeContainerHelper(name);
      atts.addAttribute(new Attribute(_Coordinate.Stagger, CDM.CurvilinearOrthogonal));
      atts.addAttribute(new Attribute(CDM.StaggerType, ll2d.toString()));

      int[] shape = new int[]{hcs.ny, hcs.nx};
      int npts = hcs.ny * hcs.nx;

      CoverageCoordAxisBuilder builder;
      if (axisType == AxisType.Lat) {
        atts.addAttribute(new Attribute(CDM.UNITS, CDM.LAT_UNITS));
        builder = new CoverageCoordAxisBuilder(name, CDM.LAT_UNITS, vindex.makeVariableDescription(), DataType.FLOAT, AxisType.Lat, atts,
                CoverageCoordAxis.DependenceType.twoD, null, CoverageCoordAxis.Spacing.irregularPoint, npts, 0, 0, 0, null, this, false);
      } else {

        atts.addAttribute(new Attribute(CDM.UNITS, CDM.LON_UNITS));
        builder = new CoverageCoordAxisBuilder(name, CDM.LON_UNITS, vindex.makeVariableDescription(), DataType.FLOAT, AxisType.Lon, atts,
                CoverageCoordAxis.DependenceType.twoD, null, CoverageCoordAxis.Spacing.irregularPoint, npts, 0, 0, 0, null, this, false);
      }

      builder.shape = shape;
      builder.userObject = vindex;
      result.add(new LatLonAxis2D( builder));
      remove.add(vindex);
    }

    // have to do this after the loop is done
    for (GribCollectionImmutable.VariableIndex vindex : remove) {
      vars.remove(vindex);
    }

    return result;
  }

  ////////////////////////////////

  private void addRuntimeCoordAxis(CoordinateRuntime runtime) {
    String units = runtime.getPeriodName() + " since " + gribCollection.getMasterFirstDate().toString();

    double offsetFromMaster = runtime.getOffsetInTimeUnits(gribCollection.getMasterFirstDate());
    List<Double> offsets = runtime.getOffsetsInTimeUnits();
    int n = offsets.size();
    double start = offsets.get(0) + offsetFromMaster;
    double end = offsets.get(n - 1) + offsetFromMaster;
    double resol2 = (n > 1) ? (end - start) / (n - 1) : 0.0;
    Counters counters = runtime.calcDistributions();
    Comparable resolMode = counters.get("resol").getMode();
    double resol = (resolMode == null) ? 0.0 : ((Number) resolMode).doubleValue();

    // CoordinateRuntime master = gribCollection.getMasterRuntime();
    //int n = master.getSize();
    boolean isScalar = (n == 1);      // this is the case of runtime[1]
    CoverageCoordAxis.DependenceType dependence = isScalar ? CoverageCoordAxis.DependenceType.scalar : CoverageCoordAxis.DependenceType.independent;
    CoverageCoordAxis.Spacing spacing = Misc.closeEnough(resol, resol2, percentTolerence) ? CoverageCoordAxis.Spacing.regular : CoverageCoordAxis.Spacing.irregularPoint;

    AttributeContainerHelper atts = new AttributeContainerHelper(runtime.getName());
    atts.addAttribute(new Attribute(CDM.UNITS, units));
    atts.addAttribute(new Attribute(CF.STANDARD_NAME, CF.TIME_REFERENCE));
    atts.addAttribute(new Attribute(CDM.LONG_NAME, "GRIB reference time"));
    atts.addAttribute(new Attribute(CF.CALENDAR, ucar.nc2.time.Calendar.proleptic_gregorian.toString()));

    CoverageCoordAxis1D result = new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder(runtime.getName(), units, "GRIB reference time", DataType.DOUBLE, AxisType.RunTime, atts,
            dependence, null, spacing, n, start, end, resol, null, this, false));

    // smoosh runtimes with similar start, end, resol
    RuntimeSmoosher tester = new RuntimeSmoosher(result);
    int index = alreadyHaveAtIndex(tester);
    if (index < 0) {
      runtimes.add(tester);
    } else {
      RuntimeSmoosher already = runtimes.get(index);
      RuntimeSmoosher combined = already.combine(result);
      runtimes.set(index, combined);        // replace with combined one
      substCoords.put(runtime.getName(), combined.runtime.getName()); // track substitutes
      System.out.printf("runtime smooch %s into %s%n", runtime.getName(), combined.runtime.getName());
    }
  }

  private int alreadyHaveAtIndex(RuntimeSmoosher tester) {
    for (int i = 0; i < runtimes.size(); i++)
      if (runtimes.get(i).closeEnough(tester)) return i;
    return -1;
  }

  private static final double percentTolerence = .10;
  private Map<String, String> substCoords = new HashMap<>();
  private List<RuntimeSmoosher> runtimes = new ArrayList<>();

  private class RuntimeSmoosher {
    final CoverageCoordAxis1D runtime;
    final double start, end, resol;
    final int npts;
    final boolean combined;

    RuntimeSmoosher(CoverageCoordAxis1D runtime, double start, double end, double resol, int npts) {
      this.runtime = runtime;
      this.start = start;
      this.end = end;
      this.resol = resol;
      this.npts = npts;
      this.combined = true;
    }

    RuntimeSmoosher(CoverageCoordAxis1D runtime) {
      this.runtime = runtime;
      this.start = runtime.getStartValue();
      this.end = runtime.getEndValue();
      this.resol = runtime.getResolution();
      this.npts = runtime.getNcoords();
      this.combined = false;
    }

    RuntimeSmoosher combine(CoverageCoordAxis1D runtime) {
      double start = Math.min(this.start, runtime.getStartValue());
      double end = Math.max(this.end, runtime.getEndValue());
      int npts = Math.max(this.npts, runtime.getNcoords());
      return new RuntimeSmoosher(this.runtime, start, end, this.resol, npts);
    }

    public boolean closeEnough(RuntimeSmoosher that) {
      double total = (end - start);
      double totalOther = (that.end - that.start);
      if (!Misc.closeEnough(totalOther, total, percentTolerence)) return false;

      double startP = Math.abs(start - that.start) / total;
      if (startP > percentTolerence) return false;

      double endP = Math.abs(end - that.end) / total;
      if (endP > percentTolerence) return false;

      double nptsP = Math.abs(npts - that.npts) / (double) npts;
      if (nptsP > percentTolerence) return false;

      return (Double.compare(that.resol, resol) == 0);
    }
  }

  private void makeRuntimeCoordAxes(List<CoverageCoordAxis> axes) {
    for (RuntimeSmoosher smoosh : runtimes) {
      if (!smoosh.combined) axes.add(smoosh.runtime);
      else {
        int n = (int) ((smoosh.end - smoosh.start) / smoosh.resol);

        CoverageCoordAxis1D combined = new CoverageCoordAxis1D(
                new CoverageCoordAxisBuilder( smoosh.runtime.getName(), smoosh.runtime.getUnits(), GribIosp.GRIB_RUNTIME, DataType.DOUBLE, AxisType.RunTime,
                new AttributeContainerHelper(smoosh.runtime.getName(), smoosh.runtime.getAttributes()),
                smoosh.runtime.getDependenceType(), null, CoverageCoordAxis.Spacing.regular, n, smoosh.start, smoosh.end, smoosh.resol, null, this, false));

        axes.add(combined);
      }
    }
  }

  //////////////////////////////////////////////////////////

  private class Time2DSmoosher {
    CoordinateTime2D time2D;
    List<? extends Object> offsets;

    public Time2DSmoosher(CoordinateTime2D time2D) {
      this.time2D = time2D;
      this.offsets = time2D.getOffsetsSorted();
      // double offsetFromMaster = time2D.getOffsetInTimeUnits(gribCollection.getMasterFirstDate());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Time2DSmoosher that = (Time2DSmoosher) o;
      return offsets.equals(that.offsets);
    }

    @Override
    public int hashCode() {
      return offsets.hashCode();
    }
  }

  private Map<Time2DSmoosher, Time2DSmoosher> time2Dmap = new HashMap<>();

  private void addTime2DCoordAxis(CoordinateTime2D time2D) {
    trackDateRange(time2D.makeCalendarDateRange(ucar.nc2.time.Calendar.proleptic_gregorian));

    Time2DSmoosher tester = new Time2DSmoosher(time2D);
    Time2DSmoosher already = time2Dmap.get(tester);
    if (already == null)
      time2Dmap.put(tester, tester);
    else
      substCoords.put(time2D.getName(), already.time2D.getName());
  }

  private void makeTime2DCoordAxis(List<CoverageCoordAxis> axes) {
    for (Time2DSmoosher smoosh : time2Dmap.keySet()) {
      CoordinateTime2D time2D = smoosh.time2D;

      List<? extends Object> offsets = time2D.getOffsetsSorted();
      int n = offsets.size();
      Counters counters = time2D.calcDistributions();
      Comparable resolMode = counters.get("resol").getMode();
      double resol = (resolMode == null) ? 0.0 : ((Number) resolMode).doubleValue();

      double start, end, resol2;
      double[] values = null;
      CoverageCoordAxis.Spacing spacing;

      if (time2D.isTimeInterval()) {
        start = ((TimeCoord.Tinv) offsets.get(0)).getBounds1();
        end = ((TimeCoord.Tinv) offsets.get(n - 1)).getBounds2();

        boolean isContiguous = true;
        for (int i = 0; i < n - 1; i++) {
          TimeCoord.Tinv tinv = (TimeCoord.Tinv) offsets.get(i);
          TimeCoord.Tinv tinv2 = (TimeCoord.Tinv) offsets.get(i + 1);
          if (!Misc.closeEnough(tinv.getBounds2(), tinv2.getBounds1()))
            isContiguous = false;
        }

        spacing = isContiguous ? CoverageCoordAxis.Spacing.contiguousInterval : CoverageCoordAxis.Spacing.discontiguousInterval;

        if (isContiguous) {
          values = new double[n + 1];
          int count = 0;
          for (Object offset : offsets)
            values[count++] = ((TimeCoord.Tinv) offset).getBounds1();
          values[count] = ((TimeCoord.Tinv) offsets.get(n - 1)).getBounds2();

        } else {
          values = new double[2 * n];
          int count = 0;
          for (Object offset : offsets) {
            TimeCoord.Tinv tinv = (TimeCoord.Tinv) offset;
            values[count++] = tinv.getBounds1();
            values[count++] = tinv.getBounds2();
          }
        }

      } else {
        start = (Integer) offsets.get(0);
        end = (Integer) offsets.get(n - 1);
        resol2 = (n > 1) ? (end - start) / (n - 1) : 0.0;

        spacing = (n == 1) || Misc.closeEnough(resol, resol2) ? CoverageCoordAxis.Spacing.regular : CoverageCoordAxis.Spacing.irregularPoint;

        if (spacing == CoverageCoordAxis.Spacing.irregularPoint) {
          values = new double[n];
          int count = 0;
          for (Object offset : offsets) {
            Integer tinv = (Integer) offset;
            values[count++] = tinv;
          }
        }
      }

      AttributeContainerHelper atts = new AttributeContainerHelper(time2D.getName());
      atts.addAttribute(new Attribute(CDM.UNITS, time2D.getUnit()));
      atts.addAttribute(new Attribute(CF.STANDARD_NAME, CF.TIME_OFFSET));
      atts.addAttribute(new Attribute(CDM.LONG_NAME, CDM.TIME_OFFSET));
      atts.addAttribute(new Attribute(CDM.UDUNITS, time2D.getTimeUdUnit()));
      atts.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.TimeOffset.toString()));

      //String reftimeName = time2D.getRuntimeCoordinate().getName();
      //String subst = substCoords.get(reftimeName);
      //if (subst != null) reftimeName = subst;

      axes.add(new TimeOffsetAxis(
              new CoverageCoordAxisBuilder( time2D.getName(), time2D.getUnit(), CDM.TIME_OFFSET, DataType.DOUBLE, AxisType.TimeOffset, atts,
              CoverageCoordAxis.DependenceType.independent, null, spacing, n, start, end, resol, values, this, false)));
    }
  }

  //////////////////////////////////////////////////////////

  private void addTimeCoordinates(CoordinateTimeAbstract time, List<CoverageCoordAxis> axes) {
    if (time instanceof CoordinateTime)
      axes.add(makeCoordAxis((CoordinateTime) time));
    else if (time instanceof CoordinateTimeIntv)
      axes.add(makeCoordAxis((CoordinateTimeIntv) time));
    CoverageCoordAxis runAux = makeRuntimeAuxCoord(time);
    if (runAux != null)
      axes.add(runAux);
  }

  private CoverageCoordAxis makeRuntimeAuxCoord(CoordinateTimeAbstract time) {
    if (time.getTime2runtime() == null) return null;
    String refName = "ref" + time.getName();

    int length = time.getSize();
    double[] data = new double[length];
    for (int i = 0; i < length; i++) data[i] = Double.NaN;

    int count = 0;
    List<Double> masterOffsets = gribCollection.getMasterRuntime().getOffsetsInTimeUnits();
    for (int masterIdx : time.getTime2runtime()) {
      data[count++] = masterOffsets.get(masterIdx - 1);
    }

    CoverageCoordAxis.Spacing spacing = CoverageCoordAxis.Spacing.irregularPoint;

    AttributeContainerHelper atts = new AttributeContainerHelper(time.getName());
    atts.addAttribute(new Attribute(CDM.UNITS, time.getTimeUdUnit()));
    atts.addAttribute(new Attribute(CF.STANDARD_NAME, CF.TIME_REFERENCE));
    atts.addAttribute(new Attribute(CDM.LONG_NAME, GribIosp.GRIB_RUNTIME));
    atts.addAttribute(new Attribute(CF.CALENDAR, ucar.nc2.time.Calendar.proleptic_gregorian.toString()));

    // LOOK lazy eval ?
    return new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder( refName, time.getTimeUdUnit(), GribIosp.GRIB_RUNTIME, DataType.DOUBLE, AxisType.RunTime, atts,
            CoverageCoordAxis.DependenceType.dependent, time.getName(), spacing, length, 0, 0, 0, data, this, false));
  }

  private CoverageCoordAxis makeCoordAxis(CoordinateTime time) {
    trackDateRange(time.makeCalendarDateRange(ucar.nc2.time.Calendar.proleptic_gregorian));
    List<Integer> offsets = time.getOffsetSorted();
    int n = offsets.size();
    double start = offsets.get(0);
    double end = offsets.get(n - 1);
    double resol2 = (n > 1) ? (end - start) / (n - 1) : 0.0;
    Counters counters = time.calcDistributions();
    Comparable resolMode = counters.get("resol").getMode();
    double resol = (resolMode == null) ? 0.0 : ((Number) resolMode).doubleValue();

    CoverageCoordAxis.Spacing spacing = Misc.closeEnough(resol, resol2) ? CoverageCoordAxis.Spacing.regular : CoverageCoordAxis.Spacing.irregularPoint;

    AttributeContainerHelper atts = new AttributeContainerHelper(time.getName());
    atts.addAttribute(new Attribute(CDM.UNITS, time.getUnit()));
    atts.addAttribute(new Attribute(CF.STANDARD_NAME, CF.TIME));
    atts.addAttribute(new Attribute(CDM.LONG_NAME, GribIosp.GRIB_VALID_TIME));
    atts.addAttribute(new Attribute(CF.CALENDAR, ucar.nc2.time.Calendar.proleptic_gregorian.toString()));

    return new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder( time.getName(), time.getTimeUdUnit(), GribIosp.GRIB_VALID_TIME, DataType.DOUBLE, AxisType.Time, atts,
            CoverageCoordAxis.DependenceType.independent, null, spacing, n, start, end, resol, null, this, false));
  }

  private CoverageCoordAxis makeCoordAxis(CoordinateTimeIntv time) {
    trackDateRange(time.makeCalendarDateRange(ucar.nc2.time.Calendar.proleptic_gregorian));
    List<TimeCoord.Tinv> offsets = time.getTimeIntervals();
    int n = offsets.size();
    double start = offsets.get(0).getBounds1();
    double end = offsets.get(n - 1).getBounds2();
    Counters counters = time.calcDistributions();
    Comparable resolMode = counters.get("resol").getMode();
    double resol = (resolMode == null) ? 0.0 : ((Number) resolMode).doubleValue();

    boolean isContiguous = true;
    for (int i = 0; i < n - 1; i++) {
      if (!Misc.closeEnough(offsets.get(1).getBounds2(), offsets.get(i + 1).getBounds1()))
        isContiguous = false;
    }
    CoverageCoordAxis.Spacing spacing = isContiguous ? CoverageCoordAxis.Spacing.contiguousInterval : CoverageCoordAxis.Spacing.discontiguousInterval;

    AttributeContainerHelper atts = new AttributeContainerHelper(time.getName());
    atts.addAttribute(new Attribute(CDM.UNITS, time.getUnit()));
    atts.addAttribute(new Attribute(CF.STANDARD_NAME, CF.TIME));
    atts.addAttribute(new Attribute(CDM.LONG_NAME, GribIosp.GRIB_VALID_TIME));
    atts.addAttribute(new Attribute(CF.CALENDAR, ucar.nc2.time.Calendar.proleptic_gregorian.toString()));

    return new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder( time.getName(), time.getTimeUdUnit(), GribIosp.GRIB_VALID_TIME, DataType.DOUBLE, AxisType.Time, atts,
            CoverageCoordAxis.DependenceType.independent, null, spacing, n, start, end, resol, null, this, false));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private CoverageCoordAxis makeCoordAxis(CoordinateVert vertCoord) {
    List<VertCoord.Level> levels = vertCoord.getLevelSorted();

    int n = vertCoord.getSize();
    double[] values;
    CoverageCoordAxis.Spacing spacing;

    if (vertCoord.isLayer()) {

      boolean isContiguous = true;
      for (int i = 0; i < n - 1; i++) {
        if (!Misc.closeEnough(levels.get(i).getValue2(), levels.get(i + 1).getValue1()))
          isContiguous = false;
      }

      spacing = isContiguous ? CoverageCoordAxis.Spacing.contiguousInterval : CoverageCoordAxis.Spacing.discontiguousInterval;

      if (isContiguous) {
        values = new double[n + 1];
        int count = 0;
        for (int i = 0; i < n; i++)
          values[count++] = levels.get(i).getValue1();
        values[count] = levels.get(n - 1).getValue2();

      } else {
        int count = 0;
        values = new double[2 * n];
        for (int i = 0; i < n; i++) {
          values[count++] = levels.get(i).getValue1();
          values[count++] = levels.get(i).getValue2();
        }
      }

    } else {

      values = new double[n];
      for (int i = 0; i < n; i++)
        values[i] = levels.get(i).getValue1();
      spacing = (n == 1) ? CoverageCoordAxis.Spacing.regular : CoverageCoordAxis.Spacing.irregularPoint;
    }

    AttributeContainerHelper atts = new AttributeContainerHelper(vertCoord.getName());
    String units = vertCoord.getUnit();
    atts.addAttribute(new Attribute(CDM.UNITS, units));
    AxisType axisType = AxisType.GeoZ;
    if (SimpleUnit.isCompatible("mbar", units))
      axisType = AxisType.Pressure;
    else if (SimpleUnit.isCompatible("m", units))
      axisType = AxisType.Height;

    String desc = vertCoord.getVertUnit().getDesc();
    if (desc != null) atts.addAttribute(new Attribute(CDM.LONG_NAME, desc));
    atts.addAttribute(new Attribute(CF.POSITIVE, vertCoord.isPositiveUp() ? CF.POSITIVE_UP : CF.POSITIVE_DOWN));

    return new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder( vertCoord.getName(), vertCoord.getUnit(), null, DataType.DOUBLE, axisType, atts,
            CoverageCoordAxis.DependenceType.independent, null, spacing, n, values[0], values[values.length - 1], 0.0, values, this, false));
  }

  private CoverageCoordAxis makeCoordAxis(CoordinateEns ensCoord) {
    int n = ensCoord.getSize();
    // LOOK likely to be regular
    double[] values = new double[n];
    for (int i = 0; i < n; i++)
      values[i] = ((EnsCoord.Coord) ensCoord.getValue(i)).getEnsMember();

    AttributeContainerHelper atts = new AttributeContainerHelper(ensCoord.getName());
    String units = ensCoord.getUnit();
    atts.addAttribute(new Attribute(CDM.UNITS, units));

    return new CoverageCoordAxis1D(
            new CoverageCoordAxisBuilder( ensCoord.getName(), units, null, DataType.DOUBLE, AxisType.Ensemble, atts,
            CoverageCoordAxis.DependenceType.independent, null, CoverageCoordAxis.Spacing.irregularPoint, ensCoord.getSize(),
            values[0], values[n - 1], 0.0, values, this, false));
  }

  private CoverageCoordSys makeCoordSys(GribCollectionImmutable.VariableIndex gribVar, List<CoverageTransform> transforms) {
    List<String> axisNames = makeAxisNameList(gribVar);
    List<String> transformNames = transforms.stream().map(CoverageTransform::getName).collect(Collectors.toList());
    return new CoverageCoordSys( null, axisNames, transformNames, coverageType);
  }

  private class NameAndType {
    String name;
    Coordinate.Type type;

    public NameAndType(String name, Coordinate.Type type) {
      this.name = name;
      this.type = type;
    }
  }

  private List<String> makeAxisNameList(GribCollectionImmutable.VariableIndex gribVar) {
    List<NameAndType> names = new ArrayList<>();
    for (Coordinate axis : gribVar.getCoordinates()) {
      String subst = substCoords.get(axis.getName());
      if (subst != null)
        names.add(new NameAndType(subst, axis.getType()));
      else
        names.add(new NameAndType(axis.getName(), axis.getType()));
      if (axis.getType() == Coordinate.Type.time || axis.getType() == Coordinate.Type.timeIntv) {
        names.add(new NameAndType("ref" + axis.getName(), Coordinate.Type.runtime));  // LOOK do we have to test if it exists??
      }
    }

    Collections.sort(names, (o1, o2) -> o1.type.order - o2.type.order);
    List<String> axisNames = names.stream().map(o -> o.name).collect(Collectors.toList());
    if (isCurvilinearOrthogonal) {
      Grib2Utils.LatLonCoordType type = Grib2Utils.getLatLon2DcoordType(gribVar.makeVariableDescription());
      switch (type) {
        case U:
          axisNames.add(Grib2Utils.LatLon2DCoord.U_Latitude.toString());
          axisNames.add(Grib2Utils.LatLon2DCoord.U_Longitude.toString());
          break;
        case V:
          axisNames.add(Grib2Utils.LatLon2DCoord.V_Latitude.toString());
          axisNames.add(Grib2Utils.LatLon2DCoord.V_Longitude.toString());
          break;
        case P:
          axisNames.add(Grib2Utils.LatLon2DCoord.P_Latitude.toString());
          axisNames.add(Grib2Utils.LatLon2DCoord.P_Longitude.toString());
          break;
      }

    } else if (isLatLon) {
      axisNames.add(CF.LATITUDE);
      axisNames.add(CF.LONGITUDE);

    } else {
      axisNames.add("y");
      axisNames.add("x");
    }

    return axisNames;
  }

  private String makeCoordSysName(List<String> axes) {
    Formatter fname = new Formatter();
    for (String axis : axes)
      fname.format(" %s", axis);
    return fname.toString();
  }

  private Coverage makeCoverage(GribCollectionImmutable.VariableIndex gribVar) {

    AttributeContainerHelper atts = new AttributeContainerHelper(gribVar.makeVariableName());
    atts.addAttribute(new Attribute(CDM.LONG_NAME, gribVar.makeVariableDescription()));
    atts.addAttribute(new Attribute(CDM.UNITS, gribVar.makeVariableUnits()));
    gribCollection.addVariableAttributes(atts, gribVar);

    /*
    GribTables.Parameter gp = gribVar.getGribParameter();
    if (gp != null) {
      if (gp.getDescription() != null)
        atts.addAttribute(new Attribute(CDM.DESCRIPTION, gp.getDescription()));
      if (gp.getAbbrev() != null)
        atts.addAttribute(new Attribute(CDM.ABBREV, gp.getAbbrev()));
      atts.addAttribute(new Attribute(CDM.MISSING_VALUE, gp.getMissing()));
      if (gp.getFill() != null)
        atts.addAttribute(new Attribute(CDM.FILL_VALUE, gp.getFill()));
    } else {
      atts.addAttribute(new Attribute(CDM.MISSING_VALUE, Float.NaN));
    }

    // statistical interval type
    if (gribVar.getIntvType() >= 0) {
      GribStatType statType = gribVar.getStatType();
      if (statType != null) {
        atts.addAttribute(new Attribute("Grib_Statistical_Interval_Type", statType.toString()));
        CF.CellMethods cm = GribStatType.getCFCellMethod(statType);
        Coordinate timeCoord = gribVar.getCoordinate(Coordinate.Type.timeIntv);
        if (cm != null && timeCoord != null)
          atts.addAttribute(new Attribute(CF.CELL_METHODS, timeCoord.getName() + ": " + cm.toString()));
      } else {
        atts.addAttribute(new Attribute("Grib_Statistical_Interval_Type", gribVar.getIntvType()));
      }
    } */

    String coordSysName = makeCoordSysName(makeAxisNameList(gribVar));

    return new Coverage(gribVar.makeVariableName(), DataType.FLOAT, atts.getAttributes(), coordSysName, gribVar.makeVariableUnits(),
            gribVar.makeVariableDescription(), this, gribVar);
  }

  //////////////////////////////////////////////////////


  @Override
  public double[] readCoordValues(CoverageCoordAxis coordAxis) throws IOException {
    if (coordAxis instanceof LatLonAxis2D)
      return readLatLonAxis2DCoordValues( (LatLonAxis2D) coordAxis);

    Optional<Coordinate> opt = group.findCoordinate(coordAxis.getName());
    if (!opt.isPresent()) throw new IllegalStateException();
    Coordinate coord = opt.get();

    if (coord instanceof CoordinateTime) {
      List<Integer> offsets = ((CoordinateTime) coord).getOffsetSorted();
      double[] values = new double[offsets.size()];
      int count = 0;
      for (int val : offsets) values[count++] = val;
      return values;

    } else if (coord instanceof CoordinateTimeIntv) {
      List<TimeCoord.Tinv> intv = ((CoordinateTimeIntv) coord).getTimeIntervals();
      double[] values;
      if (coordAxis.getSpacing() == CoverageCoordAxis.Spacing.discontiguousInterval) {
        values = new double[2 * intv.size()];
        int count = 0;
        for (TimeCoord.Tinv val : intv) {
          values[count++] = val.getBounds1();
          values[count++] = val.getBounds2();
        }
      } else {
        values = new double[intv.size() + 1];
        int count = 0;
        for (TimeCoord.Tinv val : intv) {
          values[count++] = val.getBounds1();
          values[count] = val.getBounds2(); // gets overritten except for the last
        }
      }
      return values;

    } else if (coord instanceof CoordinateRuntime) {
      List<Double> offsets = ((CoordinateRuntime) coord).getOffsetsInTimeUnits();
      double[] values = new double[offsets.size()];
      int count = 0;
      for (double val : offsets) {
        values[count++] = val;
      }
      return values;
    }

    throw new IllegalStateException();
  }

  public double[] readLatLonAxis2DCoordValues(LatLonAxis2D coordAxis) throws IOException {
    GribCollectionImmutable.VariableIndex vindex = (GribCollectionImmutable.VariableIndex) coordAxis.getUserObject();
    int[] shape = coordAxis.getShape();
    List<RangeIterator> ranges = new ArrayList<>();
    List<Integer> fullShape = new ArrayList<>();
    for (Coordinate coord : vindex.getCoordinates()) {
      ranges.add(new Range(1));
      fullShape.add(coord.getNCoords());
    }
    ranges.add( new Range(shape[0]));
    fullShape.add( shape[0]);
    ranges.add( new Range(shape[1]));
    fullShape.add( shape[1]);
    SectionIterable siter = new SectionIterable(ranges, fullShape);

    GribDataReader dataReader = GribDataReader.factory(gribCollection, vindex);
    Array data;
    try {
      data = dataReader.readData(siter); // optimize pass in null ??
    } catch (InvalidRangeException e) {
      throw new RuntimeException(e);
    }

    return (double []) data.get1DJavaArray(DataType.DOUBLE);  // LOOK lame conversion
  }


    //////////////////////////////////////////////////////

  @Override
  public GeoReferencedArray readData(Coverage coverage, SubsetParams params, boolean canonicalOrder) throws IOException, InvalidRangeException {
    GribCollectionImmutable.VariableIndex vindex = (GribCollectionImmutable.VariableIndex) coverage.getUserObject();
    CoverageCoordSys orgCoordSys = coverage.getCoordSys();
    ucar.nc2.util.Optional<CoverageCoordSys> opt = orgCoordSys.subset(params, false, true);
    if (!opt.isPresent())
      throw new InvalidRangeException(opt.getErrorMessage());

    CoverageCoordSys subsetCoordSys = opt.get();
    List<CoverageCoordAxis> coordsSetAxes = new ArrayList<>(); // for CoordsSet.factory()

    // this orders the coords based on the grib coords, which also orders the iterator in CoordsSet. could be different i think
    for (Coordinate gribCoord : vindex.getCoordinates()) {

      switch (gribCoord.getType()) {
        case runtime:
          CoverageCoordAxis runAxis = subsetCoordSys.getAxis(AxisType.RunTime);
          coordsSetAxes.addAll( axisAndDependents(runAxis, subsetCoordSys));
          break;

        case time2D:
          if (subsetCoordSys.isConstantForecast()) {
            CoverageCoordAxis toAxis = subsetCoordSys.getAxis(AxisType.TimeOffset); // aux
            coordsSetAxes.addAll(axisAndDependents(toAxis, subsetCoordSys));

            //CoverageCoordAxis1D timeAxis = (CoverageCoordAxis1D) subsetCoordSys.getAxis(AxisType.Time); // scalar
            //coordsSetAxes.add(timeAxis);

          } else { // everything else is a subset on the timeOffset
            CoverageCoordAxis toAxis = subsetCoordSys.getAxis(AxisType.TimeOffset);
            coordsSetAxes.addAll( axisAndDependents(toAxis, subsetCoordSys));

            //runAxis = subsetCoordSys.getAxis(AxisType.RunTime); // in case its an aux, for Best
            //if (!hasRuntime  && runAxis != null) // can it be null ?                  // LOOK could also try getDependent()
            //  coordsSetAxes.add(runAxis);
          }
          break;

        case vert:
          CoverageCoordAxis1D vertAxis = (CoverageCoordAxis1D) subsetCoordSys.getZAxis();
          coordsSetAxes.add(vertAxis);
          break;

        case time:
        case timeIntv:
        case ens:
          CoverageCoordAxis axis = subsetCoordSys.getAxis(gribCoord.getType().axisType);
          coordsSetAxes.addAll( axisAndDependents(axis, subsetCoordSys));
          break;
      }
    }

    boolean hasruntime = false;
    for (CoverageCoordAxis axis : coordsSetAxes )
      if (axis.getAxisType() == AxisType.RunTime) hasruntime = true;
    if (!hasruntime)
      System.out.printf("HEY no runtime%n");

    List<CoverageCoordAxis> geoArrayAxes = new ArrayList<>(coordsSetAxes);  // for GeoReferencedArray
    geoArrayAxes.add(subsetCoordSys.getYAxis());
    geoArrayAxes.add(subsetCoordSys.getXAxis());
    List<RangeIterator> yxRange = subsetCoordSys.getHorizCoordSys().getRanges(); // may be 2D

    // iterator over all except x, y
    CoordsSet coordIter = CoordsSet.factory(subsetCoordSys.isConstantForecast(), coordsSetAxes);

    GribDataReader dataReader = GribDataReader.factory(gribCollection, vindex);
    Array data = dataReader.readData2(coordIter, yxRange.get(0), yxRange.get(1));

    return new GeoReferencedArray(coverage.getName(), coverage.getDataType(), data, subsetCoordSys);

    // return new GeoReferencedArray(coverage.getName(), coverage.getDataType(), data, geoArrayAxes, orgCoordSys.getTransforms(), subsetCoordSys.getType());
  }

  // LOOK dependent axis could get added multiple times
  private List<CoverageCoordAxis> axisAndDependents( CoverageCoordAxis axis, CoverageCoordSys csys) {
    List<CoverageCoordAxis> result = new ArrayList<>();
    if (axis.getDependenceType() != CoverageCoordAxis.DependenceType.dependent)
      result.add(axis);
    for (CoverageCoordAxis dependent : csys.getDependentAxes(axis))
      result.add(dependent);
    return result;
  }

  /*
  RangeIterator subsetRuntime(CoordinateRuntime gribCoord, CoverageCoordAxis1D covAxisSubset) throws InvalidRangeException {
    if (!covAxisSubset.isSubset())
      return new Range(gribCoord.getSize()); // get all of it

    List<Integer> idxIntoSA = new ArrayList<>(covAxisSubset.getNcoords());
    for (int i=0; i<covAxisSubset.getNcoords(); i++) {
      double val = covAxisSubset.getCoord(i);
      CalendarDate cdval = covAxisSubset.makeDate(val);
      int idx =  gribCoord.getIndex(cdval.getMillis());  // must be exact
      idxIntoSA.add(idx);
    }

    if (idxIntoSA.size() == 1) {
      int wantIdx = idxIntoSA.get(0);
      return new Range(gribCoord.getName(), wantIdx, wantIdx);
    } else {
      int[] vals = new int[idxIntoSA.size()];
      for (int i=0; i<idxIntoSA.size(); i++) vals[i] = idxIntoSA.get(i);
      return new RangeScatter(gribCoord.getName(), vals);
    }
  }

  // this only allows for the case of rectangular (runtime, offset) or (runtime=1, time) queries.
  // (runtime, offset=1) = constant offset
  // (runtime=1, time) = constant runtime

  // CANT DO YET (HOW - switch timeOffset to CoverageCoordAxis1D time)  (NO: dataReader.readData() only does rectangle. need new API)
  // (runtime > 1, time = 1)  = constant forecast   (must cut down the runtime based on the time request - only those that have it.
  // (runtime > 1, time > 1).

  RangeIterator subsetTimeOffset(CoordinateTime2D gribCoord, CoverageCoordAxis1D runAxis, CoverageCoordAxis1D toAxis) throws InvalidRangeException {

    double rundateVal = runAxis.getCoord(0);
    CalendarDate rundate = runAxis.makeDate(rundateVal);
    CoordinateRuntime gribRuntime = gribCoord.getRuntimeCoordinate();
    int runIdx =  gribRuntime.getIndex(rundate.getMillis());  // must be exact

    List<Integer> idxIntoSA = new ArrayList<>(toAxis.getNcoords());
    for (int i=0; i<toAxis.getNcoords(); i++) {
      double val = toAxis.getCoord(i);
      int idx = gribCoord.findIndexContaining(runIdx, val, rundate);
      idxIntoSA.add(idx);
    }

    if (idxIntoSA.size() == 1) {
      int wantIdx = idxIntoSA.get(0);
      return new Range(gribCoord.getName(), wantIdx, wantIdx);
    } else {
      int[] vals = new int[idxIntoSA.size()];
      for (int i=0; i<idxIntoSA.size(); i++) vals[i] = idxIntoSA.get(i);
      return new RangeScatter(gribCoord.getName(), vals);
    }
  }

  RangeIterator subset(Coordinate gribCoord, CoverageCoordAxis1D covAxisSubset) throws InvalidRangeException {
    if (!covAxisSubset.isSubset())
      return new Range(gribCoord.getSize()); // get all of it

    List<Integer> idxIntoSA = new ArrayList<>(covAxisSubset.getNcoords());
    for (int i=0; i<covAxisSubset.getNcoords(); i++) {
      double need = covAxisSubset.getCoord(i);
      int idx =  gribCoord.findIndexContaining(need);
      idxIntoSA.add(idx);
    }

    if (idxIntoSA.size() == 1) {
      int wantIdx = idxIntoSA.get(0);
      return new Range(gribCoord.getName(), wantIdx, wantIdx);
    } else {
      int[] vals = new int[idxIntoSA.size()];
      for (int i=0; i<idxIntoSA.size(); i++) vals[i] = idxIntoSA.get(i);
      return new RangeScatter(gribCoord.getName(), vals);
    }
  } */


}
