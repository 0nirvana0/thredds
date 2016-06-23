/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.nc2;

import dap4.cdm.CDMTypeFcns;
import dap4.cdm.CDMUtil;
import dap4.core.data.DSP;
import dap4.core.data.DataAtomic;
import dap4.core.data.DataCompoundArray;
import dap4.core.data.DataDataset;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.dap4lib.*;
import ucar.ma2.*;
import ucar.nc2.Group;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Implementation of ArrayStructure that wraps
 * DAP4 databuffer
 * (Note: Needs serious optimization applied).
 * <p/>
 * Given
 * Structure S {f1,f2,...fm} [d1][d2]...[dn],
 * internally, this is stored as a 2-D array
 * CDMArray[][] instances;
 * The first dimension's length is d1*d2*...dn.
 * The second dimension has size |members| i.e. the number
 * of fields in the sequence.
 */

public class CDMArrayStructure extends ArrayStructure implements CDMArray
{
    //////////////////////////////////////////////////
    // Instance variables

    // CDMArry variables
    protected DataDataset root = null;
    protected Group cdmroot = null;
    protected DSP dsp = null;
    protected DapVariable template = null;
    protected long bytesize = 0;
    protected DapType basetype = null;

    protected DataCompoundArray data = null;
    protected long dimsize = 0;
    protected long nmembers = 0;

    /**
     * Since we are using StructureData,
     * we do not actually need to keep the
     * DataStructure instances as such.
     * We need a mapping from index X member to a
     * CDMArray object.
     * Total number of objects is dimsize * |members|.
     * Accessed by StructureData.
     */

    protected Array[][] instances = null;


    //////////////////////////////////////////////////
    // Constructor(s)

    /**
     * Constructor
     *
     * @param dsp    the parent DSP
     * @param cdmroot   the parent CDMDataset
     * @param data the structure data
     */
    CDMArrayStructure(DSP dsp, Group cdmroot, DataCompoundArray data)
    {
        super(computemembers((DapStructure) data.getTemplate()),
                CDMUtil.computeEffectiveShape(((DapVariable) data.getTemplate()).getDimensions()));
        this.dsp = dsp;
        this.cdmroot = cdmroot;
        this.template = (DapVariable) data.getTemplate();
        this.basetype = this.template.getBaseType();

        this.dimsize = DapUtil.dimProduct(template.getDimensions());
        this.data = data;
        this.nmembers = ((DapStructure) template).getFields().size();

        // Fill in the instances and structdata vectors
        // The leaf instances arrays will be filled in by the CDM compiler
        super.sdata = new StructureDataA[(int) this.dimsize];
        instances = new Array[(int) this.dimsize][(int) this.nmembers];
        for(int i = 0; i < dimsize; i++) {
            super.sdata[i] = new StructureDataA(this, i);
            instances[i] = new Array[(int) this.nmembers];
        }
    }

    /*package access*/ void
    finish()
    {
        for(int i = 0; i < this.dimsize; i++) {
            assert instances[i] != null;
        }
        this.bytesize = computeTotalSize();
    }

    //////////////////////////////////////////////////
    // CDMArry Interface

    @Override
    public DSP getDSP()
    {
        return dsp;
    }

    @Override
    public DataDataset getRoot()
    {
        return root;
    }

    @Override
    public DapVariable getTemplate()
    {
        return template;
    }

    @Override
    public DapType getBaseType()
    {
        return this.basetype;
    }

    //////////////////////////////////////////////////
    // Accessors

    @Override
    public long getSize()
    {
        return this.dimsize;
    }

    void addField(long pos, StructureMembers.Member m, Array instance)
    {
        int mindex = memberIndex(m);
        addField(pos, mindex, instance);
    }

    void addField(long offset, int mindex, Array instance)
    {
        assert this.instances != null : "Internal Error";
        if(offset < 0 || offset >= this.dimsize)
            throw new ArrayIndexOutOfBoundsException("CDMArrayStructure: dimension index out of range: " + offset);
        if(mindex < 0 || mindex >= this.nmembers)
            throw new ArrayIndexOutOfBoundsException("CDMArrayStructure: member index out of range: " + mindex);
        this.instances[(int) offset][mindex] = instance; // WARNING: overwrites
    }

    //////////////////////////////////////////////////

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        DapStructure struct = (DapStructure) this.template;
        for(int i = 0; i < this.dimsize; i++) {
            List<DapVariable> fields = struct.getFields();
            if(i < (this.dimsize - 1))
                buf.append("\n");
            buf.append("Structure {\n");
            if(fields != null) {
                int nmembers = fields.size();
                for(int j = 0; j < nmembers; j++) {
                    DapVariable field = fields.get(j);
                    String sfield;
                    Array array = instances[i][j];
                    sfield = (array == null ? "null" : array.toString());
                    buf.append(sfield + "\n");
                }
            }
            buf.append(String.format("} [%d/%d]", i, dimsize));
        }
        return buf.toString();
    }

    public long
    computeTotalSize()
    {
        long totalsize = 0;
        for(int recno = 0; recno < this.dimsize; recno++) {
            for(int m = 0; m < this.nmembers; m++) {
                totalsize += instances[recno][m].getSizeBytes();
            }
        }
        return totalsize;
    }

    //////////////////////////////////////////////////
    // ArrayStructure interface

    /**
     * Get the index'th StructureData(StructureDataA) object
     * We need instances of StructureData to give to the user.
     * We use StructureDataA so we can centralize everything
     * in this class. The total number of StructureData objects
     * is dimsize.
     *
     * @param index
     * @return
     */
    @Override
    public StructureData getStructureData(int index)
    {
        assert(super.sdata != null);
        if(index < 0 || index >= this.dimsize)
            throw new IllegalArgumentException(index + " >= " + super.sdata.length);
        assert (super.sdata[index] != null);
        return super.sdata[index];
    }

    /**
     * Key interface method coming in from StructureDataA.
     *
     * @param recno The instance # of the array of Structure instances
     * @param m     The member of interest in the Structure instance
     * @return The ucar.ma2.Array instance corresponding to the instance.
     */
    public ucar.ma2.Array
    getArray(int recno, StructureMembers.Member m)
    {
        return (ucar.ma2.Array) memberArray(recno, memberIndex(m));
    }

    public double getScalarDouble(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getDouble(0);
    }

    public float
    getScalarFloat(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getFloat(0);
    }

    public byte
    getScalarByte(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getByte(0);
    }

    public short
    getScalarShort(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getShort(0);
    }

    public int
    getScalarInt(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getInt(0);
    }

    public long
    getScalarLong(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getLong(0);
    }

    public char
    getScalarChar(int index, StructureMembers.Member m)
    {
        CDMArrayAtomic data = getAtomicArray(index, m);
        return data.getChar(0);
    }

    /**
     * Get member databuffer of type String or char.
     *
     * @param recnum get databuffer from the recnum-th StructureData of the ArrayStructure. Must be less than getSize();
     * @param m      get databuffer from this StructureMembers.Member. Must be of type String or char.
     * @return scalar String value
     */
    public String getScalarString(int recnum, StructureMembers.Member m)
    {
        Array data = m.getDataArray();
        return (String) data.getObject(recnum).toString();
    }

    public double[] getJavaArrayDouble(int recnum, StructureMembers.Member m)
    {
        CDMArrayAtomic array = getAtomicArray(recnum, m);
        if(!array.getBaseType().isNumericType())
            throw new IllegalArgumentException("Cannot convert non-numeric type");
        DataAtomic data = array.getData();
        DapType atomtype = data.getType();
        long nelems = data.getCount();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (double[]) LibTypeFcns.convertVector(DapType.FLOAT64, atomtype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public float[]
    getJavaArrayFloat(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (float[]) LibTypeFcns.convertVector(DapType.FLOAT32, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public byte[]
    getJavaArrayByte(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (byte[]) LibTypeFcns.convertVector(DapType.INT8, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public short[]
    getJavaArrayShort(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (short[]) LibTypeFcns.convertVector(DapType.INT16, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public int[]
    getJavaArrayInt(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (int[]) LibTypeFcns.convertVector(DapType.INT32, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public long[]
    getJavaArrayLong(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (long[]) LibTypeFcns.convertVector(DapType.INT64, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public char[]
    getJavaArrayChar(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (char[]) LibTypeFcns.convertVector(DapType.CHAR, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public String[]
    getJavaArrayString(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (String[]) LibTypeFcns.convertVector(DapType.STRING, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    public ByteBuffer[]
    getJavaArrayOpaque(int index, StructureMembers.Member m)
    {
        DataAtomic data = getAtomicArray(index, m).getData();
        DapType atype = data.getType();
        long count = atype.getSize();
        try {
            List<Slice> slices = CDMUtil.shapeToSlices(m.getShape());
            Object vector = data.read(slices);
            return (ByteBuffer[]) LibTypeFcns.convertVector(DapType.OPAQUE, atype, vector);
        } catch (DapException de) {
            throw new UnsupportedOperationException(de);
        }
    }

    // Non-atomic cases

    public StructureData getScalarStructure(int index, StructureMembers.Member m)
    {
        if(m.getDataType() != DataType.STRUCTURE)
            throw new ForbiddenConversionException("Atomic field cannot be converted to Structure");
        Array ca = memberArray(index, memberIndex(m));
        if(ca.getDataType() != DataType.STRUCTURE && ca.getDataType() != DataType.SEQUENCE)
            throw new ForbiddenConversionException("Attempt to access non-structure member");
        CDMArrayStructure as = (CDMArrayStructure) ca;
        return as.getStructureData(0);
    }

    public ArrayStructure getArrayStructure(int index, StructureMembers.Member m)
    {
        if(m.getDataType() != DataType.STRUCTURE)
            throw new ForbiddenConversionException("Atomic field cannot be converted to Structure");
        Array dd = memberArray(index, memberIndex(m));
        if(dd.getDataType() != DataType.STRUCTURE && dd.getDataType() != DataType.SEQUENCE)
            throw new ForbiddenConversionException("Attempt to access non-structure member");
        return (CDMArrayStructure) dd;
    }

    public ArraySequence getArraySequence(StructureMembers.Member m)
    {
        throw new UnsupportedOperationException("CDMArraySequence");
    }

    @Override
    public Array copy()
    {
        return this; // temporary
    }

    //////////////////////////////////////////////////
    // Utilities

    @Override
    protected StructureData
    makeStructureData(ArrayStructure as, int index)
    {
        if(super.sdata[index] == null)
            super.sdata[index] = new StructureDataA(as, index);
        return super.sdata[index];
    }

    /**
     * Compute the StructureMembers object
     * from a DapStructure. May need to recurse
     * if a field is itself a Structure
     *
     * @param ds The DapStructure to use to construct
     *           a StructureMembers object.
     * @return The StructureMembers object for the given DapStructure
     */
    static StructureMembers
    computemembers(DapStructure ds)
    {
        StructureMembers sm
                = new StructureMembers(ds.getShortName());
        List<DapVariable> fields = ds.getFields();
        for(int i = 0; i < fields.size(); i++) {
            DapVariable field = fields.get(i);
            StructureMembers.Member m =
                    sm.addMember(
                            field.getShortName(), "", null,
                            CDMTypeFcns.daptype2cdmtype(field.getBaseType()),
                            CDMUtil.computeEffectiveShape(ds.getDimensions()));
            m.setDataParam(i); // So we can index into various lists
            // recurse if this field is itself a structure
            if(field.getSort() == DapSort.STRUCTURE) {
                StructureMembers subsm = computemembers((DapStructure) field);
                m.setStructureMembers(subsm);
            }
        }
        return sm;
    }

    protected Array
    memberArray(int recno, int memberindex)
    {
        Array cdmdata = instances[recno][memberindex];
        return cdmdata;
    }

    static protected int
    memberIndex(StructureMembers.Member m)
    {
        return m.getDataParam();
    }

    protected CDMArrayAtomic
    getAtomicArray(int index, StructureMembers.Member m)
    {
        Array dd = memberArray(index, memberIndex(m));
        if(dd.getDataType() != DataType.STRUCTURE && dd.getDataType() != DataType.SEQUENCE)
            return (CDMArrayAtomic) dd;
        throw new ForbiddenConversionException("Cannot convert structure to AtomicArray");
    }

}
