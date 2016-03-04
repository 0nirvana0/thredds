/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4shared;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Nc4Factory extends DefaultFactory
{

    // Nc4DSP Uses two annotations keys: one to track ids
    // and one to track all nodes in tree by FQN
    static public final int NC4DSPKEY = "NC4DSPKEY".hashCode();

    //////////////////////////////////////////////////

    protected Stack<DapNode> scope = new Stack<>();

    // Collect all created nodes
    protected List<DapNode> allnodes = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor
    public Nc4Factory()
    {
        super();
    }

    //////////////////////////////////////////////////
    // Accessors

    public List<DapNode>
    getAllNodes()
    {
        List<DapNode> nodes = this.allnodes;
        this.allnodes = null;
        return nodes;
    }

    void enterContainer(DapNode c)
    {
        scope.push(c);
    }

    void leaveContainer()
    {
        scope.pop();
    }

    //////////////////////////////////////////////////
    // Annotation management

    protected DapNode
    tag(DapNode container, DapNode annotatednode, int id)
    {
        DapNode n = tag(annotatednode, id);
        if(container != null) try {
            switch (container.getSort()) {
            case GROUP:
                ((DapGroup) container).addDecl(n);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) container).addField(n);
                break;
            default:
                throw new DapException("Nc4Factory: unexpected container: " + container);
            }
        } catch (DapException e) {
            throw new IllegalStateException(e);
        }
        return n;
    }

    protected DapNode
    tag(DapNode annotatednode, int id)
    {
        DapNode parent = (scope.empty() ? null : scope.peek());
        int gid = (parent == null ? -1
                : ((NetcdfDSP.Nc4ID) parent.annotation(NC4DSPKEY)).gid);
        NetcdfDSP.Nc4ID nid = new NetcdfDSP.Nc4ID(gid, id);
        annotatednode.annotate(NC4DSPKEY, nid);
        allnodes.add(annotatednode);
        return annotatednode;
    }

    /**
     * Return gid of containing group
     */
    public int
    getGID(DapNode node)
    {
        NetcdfDSP.Nc4ID id = (NetcdfDSP.Nc4ID) node.annotation(NC4DSPKEY);
        return id.gid;
    }

    /**
     * Return id of object
     */
    public int
    getID(DapNode node)
    {
        NetcdfDSP.Nc4ID id = (NetcdfDSP.Nc4ID) node.annotation(NC4DSPKEY);
        return id.id;
    }

    //////////////////////////////////////////////////
    // DapFactory Extended API

    public DapAttribute newAttribute(String name, DapType basetype)
    {
        return (DapAttribute) tag(super.newAttribute(name, basetype), -1);
    }

    public DapAttributeSet newAttributeSet(String name)
    {
        return (DapAttributeSet) tag(super.newAttributeSet(name), -1);
    }

    public DapOtherXML newOtherXML(String name)
    {
        return (DapOtherXML) tag(super.newOtherXML(name), -1);
    }

    //////////////////////////////////////////////////
    // "Top Level"  nodes

    public DapDimension newDimension(String name, long size, int id)
    {
        return (DapDimension) tag(scope.peek(), super.newDimension(name, size), id);
    }

    public DapMap newMap(DapVariable target, int id)
    {
        return (DapMap) tag(scope.peek(), super.newMap(target), id);
    }

    public DapVariable newAtomicVariable(String name, DapType t, int id)
    {
        return (DapVariable) tag(scope.peek(), super.newAtomicVariable(name, t), id);
    }

    public DapVariable newVariable(String name, DapType t, int id)
    {
        return (DapVariable) tag(scope.peek(), super.newVariable(name, t), id);
    }

    public DapGroup newGroup(String name, int id)
    {
        return (DapGroup) tag(scope.peek(), super.newGroup(name), id);
    }

    public DapDataset newDataset(String name, int id)
    {
        return (DapDataset) tag(scope.peek(), super.newDataset(name), id);
    }

    public DapEnumeration newEnumeration(String name, DapType basetype, int id)
    {
        return (DapEnumeration) tag(scope.peek(), super.newEnumeration(name, basetype), id);
    }

    public DapEnumConst newEnumConst(String name, long value, DapEnumeration parent, int id)
    {
        return (DapEnumConst) tag(scope.peek(), super.newEnumConst(name, value, parent), id);
    }

    public DapStructure newStructure(String name, int id)
    {
        return (DapStructure) tag(scope.peek(), super.newStructure(name), id);
    }

    public DapSequence newSequence(String name, int id)
    {
        return (DapSequence) tag(scope.peek(), super.newSequence(name), id);
    }

}

