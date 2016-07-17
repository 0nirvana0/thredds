/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.HashMap;
import java.util.Map;

import static dap4.dap4lib.netcdf.DapNetcdf.*;
import static dap4.dap4lib.netcdf.Nc4Notes.*;

public class Nc4DMR
{
    //////////////////////////////////////////////////

    static public class Nc4Attribute extends DapAttribute
    {
        public Nc4Attribute(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class Nc4AttributeSet extends DapAttributeSet
    {
        public Nc4AttributeSet(String name)
        {
            super(name);
        }
    }

    static public class Nc4Dimension extends DapDimension
    {
        public Nc4Dimension(String name, long size)
        {
            super(name, size);
        }
    }

    static public class Nc4Map extends DapMap
    {
        public Nc4Map(DapVariable target)
        {
            super(target);
        }
    }

    static public class Nc4AtomicVariable extends DapAtomicVariable
    {
        public Nc4AtomicVariable(String name, DapType t)
        {
            super(name, t);
        }
    }

    abstract static public class Nc4Variable extends DapVariable
    {
        public Nc4Variable(String name)
        {
            super(name);
        }
    }

    static public class Nc4Group extends DapGroup
    {
        public Nc4Group(String name)
        {
            super(name);
        }
    }

    static public class Nc4Dataset extends DapDataset
    {
        public Nc4Dataset(String name)
        {
            super(name);
        }
    }

    static public class Nc4Enumeration extends DapEnumeration
    {
        public Nc4Enumeration(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class Nc4EnumConst extends DapEnumConst
    {
        public Nc4EnumConst(String name, long value)
        {
            super(name, value);
        }
    }

    static public class Nc4Structure extends DapStructure
    {
        public Nc4Structure(String name)
        {
            super(name);
        }
    }

    static public class Nc4Sequence extends DapSequence
    {
        public Nc4Sequence(String name)
        {
            super(name);
        }
    }

    static public class Nc4OtherXML extends DapOtherXML
    {
        public Nc4OtherXML(String name)
        {
            super(name);
        }
    }
}
