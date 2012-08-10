/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.core.model.instance;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.expr.XPathExpression;

public class TreeReference implements Externalizable {
	public static final int DEFAULT_MUTLIPLICITY = 0;//multiplicity
	public static final int INDEX_UNBOUND = -1;//multiplicity
	public static final int INDEX_TEMPLATE = -2;//multiplicity
	public static final int INDEX_ATTRIBUTE = -4;//multiplicity flag for an attribute
	public static final int INDEX_REPEAT_JUNCTURE = -10;
	
	public static final int REF_ABSOLUTE = -1;
	
	public static final String NAME_WILDCARD = "*";
	
	private int refLevel; //0 = context node, 1 = parent, 2 = grandparent ...
	private Vector names; //Vector<String>
	private Vector multiplicity; //Vector<Integer>
	//private Vector<XPathExpression> predicates; //Vector<XPathExpression>
	private Hashtable<Integer, Vector<XPathExpression>> predicates;
	private FormInstance instance = null;
	private String instanceName = null;
	

	public static TreeReference rootRef () {
		TreeReference root = new TreeReference();
		root.refLevel = REF_ABSOLUTE;
		return root;
	}
	
	public static TreeReference selfRef () {
		TreeReference self = new TreeReference();
		self.refLevel = 0;
		return self;
	}
	
	public TreeReference () {
		names = new Vector(0);
		multiplicity = new Vector(0);		
		predicates = new Hashtable<Integer, Vector<XPathExpression>>();
		instance = null; //null means the default instance
		instanceName = null; //dido
	}
	
	public String getInstanceName() {
		return instanceName;
	}

	public void setInstanceName(String instanceName) {
		this.instanceName = instanceName;
	}

	public FormInstance getInstance() {
		return instance;
	}

	public void setInstance(FormInstance instance) {
		this.instance = instance;
	}
	
	public int getMultiplicity(int index) {
		return ((Integer)multiplicity.elementAt(index)).intValue();
	}
	
	public String getName(int index) {
		return (String)names.elementAt(index);
	}

	public int getMultLast () {
		return ((Integer)multiplicity.lastElement()).intValue();
	}
	
	public String getNameLast () {
		return (String)names.lastElement();
	}
	
	public void setMultiplicity (int i, int mult) {
		multiplicity.setElementAt(new Integer(mult), i);
	}
	
	public int size () {
		return names.size();
	}
	
	public void add (String name, int index) {
		names.addElement(name);
		multiplicity.addElement(new Integer(index));
	}
	
	public void addPredicate(int key, Vector<XPathExpression> xpe)
	{
		predicates.put(new Integer(key), xpe);
	}
	
	public Vector<XPathExpression> getPredicate(int key)
	{
		return predicates.get(new Integer(key));
	}
	
	public int getRefLevel () {
		return refLevel;
	}
	
	public void setRefLevel (int refLevel) {
		this.refLevel = refLevel;
	}
	
	public void incrementRefLevel () {
		if (!isAbsolute()) {
			refLevel++;
		}
	}
	
	public boolean isAbsolute () {
		return refLevel == REF_ABSOLUTE;
	}
	
	//return true if this ref contains any unbound multiplicities... ie, there is ANY chance this ref
	//could ambiguously refer to more than one instance node.
	public boolean isAmbiguous () {
		//ignore level 0, as /data implies /data[0]
		for (int i = 1; i < size(); i++) {
			if (getMultiplicity(i) == INDEX_UNBOUND) {
				return true;
			}
		}
		return false;
	}
	
	//return a copy of the ref
	public TreeReference clone () {
		TreeReference newRef = new TreeReference();
		newRef.setRefLevel(this.refLevel);
		for (int i = 0; i < this.size(); i++) {
			newRef.add(this.getName(i), this.getMultiplicity(i));
		}
		//copy predicates
		for(Enumeration en = predicates.keys(); en.hasMoreElements(); )
		{
			Integer i = ((Integer)en.nextElement());
			newRef.addPredicate(i.intValue(), predicates.get(i));
		}
		//copy instances
		if(instanceName != null)
		{
			newRef.setInstanceName(instanceName);
		}
		if(instance != null)
		{
			newRef.setInstance(instance);
		}
		return newRef;
	}
	
	/*
	 * chop the lowest level off the ref so that the ref now represents the parent of the original ref
	 * return true if we successfully got the parent, false if there were no higher levels
	 */
	public boolean removeLastLevel () {
		int size = size();
		if (size == 0) {
			if (isAbsolute()) {
				return false;
			} else {
				refLevel++;
				return true;
			}
		} else {
			names.removeElementAt(size - 1);
			multiplicity.removeElementAt(size - 1);
			return true;
		}
	}
	
	public TreeReference getParentRef () {
		//TODO: level
		TreeReference ref = this.clone();
		if (ref.removeLastLevel()) {
			return ref;
		} else {
			return null;
		}
	}
	
	//return a new reference that is this reference anchored to a passed-in parent reference
	//if this reference is absolute, return self
	//if this ref has 'parent' steps (..), it can only be anchored if the parent ref is a relative ref consisting only of other 'parent' steps
	//return null in these invalid situations
	public TreeReference parent (TreeReference parentRef) {
		if (isAbsolute()) {
			return this;
		} else {
			TreeReference newRef = parentRef.clone();
			
			if (refLevel > 0) {
				if (!parentRef.isAbsolute() && parentRef.size() == 0) {
					parentRef.refLevel += refLevel;
				} else {
					return null;
				}
			}
			
			for (int i = 0; i < names.size(); i++) {
				newRef.add(this.getName(i), this.getMultiplicity(i));
			}

			return newRef;			
		}
	}
	
	
	//very similar to parent(), but assumes contextRef refers to a singular, existing node in the model
	//this means we can do '/a/b/c + ../../d/e/f = /a/d/e/f', which we couldn't do in parent()
	//return null if context ref is not absolute, or we parent up past the root node
	//NOTE: this function still works even when contextRef contains INDEX_UNBOUND multiplicites... conditions depend on this behavior,
	//  even though it's slightly icky
	public TreeReference anchor (TreeReference contextRef) {
		if (isAbsolute()) {
			return this.clone();
		} else if (!contextRef.isAbsolute()) {
			return null;
		} else {
			TreeReference newRef = contextRef.clone();
			int contextSize = contextRef.size();
			if (refLevel > contextSize) {
				return null; //tried to do '/..'
			} else {			
				for (int i = 0; i < refLevel; i++) {
					newRef.removeLastLevel();
				}
				for (int i = 0; i < size(); i++) {
					newRef.add(this.getName(i), this.getMultiplicity(i));
				}
				//copy predicates
				for(Enumeration en = predicates.keys(); en.hasMoreElements(); )
				{
					Integer i = ((Integer)en.nextElement());
					newRef.addPredicate(i.intValue(), predicates.get(i));
				}
				return newRef;
			}
		}
	}
	
	//TODO: merge anchor() and parent()
		
	public TreeReference contextualize (TreeReference contextRef) {
		if (!contextRef.isAbsolute()){
			return null;
		}
		TreeReference newRef = anchor(contextRef);
		
		for (int i = 0; i < contextRef.size() && i < newRef.size(); i++) {
			
			//If the the contextRef can provide a definition for a wildcard, do so
			if(TreeReference.NAME_WILDCARD.equals(newRef.getName(i)) && !TreeReference.NAME_WILDCARD.equals(contextRef.getName(i))) {
				newRef.names.setElementAt(contextRef.getName(i), i);
			}
			
			if (contextRef.getName(i).equals(newRef.getName(i))) {
				newRef.setMultiplicity(i, contextRef.getMultiplicity(i));
			} else {
				break;
			}
		}

		return newRef;
	}
	
	public TreeReference relativize (TreeReference parent) {
		if (parent.isParentOf(this, false)) {
			TreeReference relRef = selfRef();
			for (int i = parent.size(); i < this.size(); i++) {
				relRef.add(this.getName(i), INDEX_UNBOUND);
			}
			return relRef;
		} else {
			return null;
		}
	}
	
	//turn unambiguous ref into a generic ref
	public TreeReference genericize () {	
		TreeReference genericRef = clone();
		for (int i = 0; i < genericRef.size(); i++) {
			genericRef.setMultiplicity(i, INDEX_UNBOUND);
		}
		return genericRef;
	}
	
	//returns true if 'this' is parent of 'child'
	//return true if 'this' equals 'child' only if properParent is false
	public boolean isParentOf (TreeReference child, boolean properParent) {
		if (refLevel != child.refLevel)
			return false;
		if (child.size() < size() + (properParent ? 1 : 0))
			return false;
		
		for (int i = 0; i < size(); i++) {
			if (!this.getName(i).equals(child.getName(i))) {
				return false;
			}
			
			int parMult = this.getMultiplicity(i);
			int childMult = child.getMultiplicity(i);
			if (parMult != INDEX_UNBOUND && parMult != childMult && !(i == 0 && parMult == 0 && childMult == INDEX_UNBOUND)) {
				return false;
			}
		}
		
		return true;
	}
		
	/**
	 * clone and extend a reference by one level
	 * @param ref
	 * @param name
	 * @param mult
	 * @return
	 */
	public TreeReference extendRef (String name, int mult) {
		//TODO: Shouldn't work for this if this is an attribute ref;
		TreeReference childRef = this.clone();
		childRef.add(name, mult);
		return childRef;
	}
	
	public boolean equals (Object o) {
		if (this == o) {
			return true;
		} else if (o instanceof TreeReference) {
			TreeReference ref = (TreeReference)o;
			
			if (this.refLevel == ref.refLevel && this.size() == ref.size()) {
				for (int i = 0; i < this.size(); i++) {
					String nameA = this.getName(i);
					String nameB = ref.getName(i);
					int multA = this.getMultiplicity(i);
					int multB = ref.getMultiplicity(i);
					
					if (!nameA.equals(nameB)) {
						return false;
					} else if (multA != multB) {
						if (i == 0 && (multA == 0 || multA == INDEX_UNBOUND) && (multB == 0 || multB == INDEX_UNBOUND)) {
							// /data and /data[0] are functionally the same
						} else {
							return false;
						}
					}
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	public int hashCode () {
		int hash = (new Integer(refLevel)).hashCode();
		for (int i = 0; i < size(); i++) {
			//NOTE(ctsims): It looks like this is only using Integer to
			//get the hashcode method, but that method
			//is just returning the int value, I think, so
			//this should potentially just be replaced by
			//an int.
			Integer mult = new Integer(getMultiplicity(i));
			if (i == 0 && mult.intValue() == INDEX_UNBOUND)
				mult = new Integer(0);
			
			hash ^= getName(i).hashCode();
			hash ^= mult.hashCode();
		}
		return hash;
	}
	
	public String toString () {
		return toString(true);
	}
	
	public String toString (boolean includePredicates) {
		StringBuffer sb = new StringBuffer();
		if(instanceName != null)
		{
			sb.append("instance("+instanceName+")");
		}
		if (isAbsolute()) {
			sb.append("/");
		} else {
			for (int i = 0; i < refLevel; i++)
				sb.append("../");
		}
		for (int i = 0; i < size(); i++) {
			String name = getName(i);
			int mult = getMultiplicity(i);
			
			if(mult == INDEX_ATTRIBUTE) {
				sb.append("@");
			}
			sb.append(name);
			
			if (includePredicates) {
				switch (mult) {
				case INDEX_UNBOUND: break;
				case INDEX_TEMPLATE: sb.append("[@template]"); break;
				case INDEX_REPEAT_JUNCTURE: sb.append("[@juncture]"); break;
				default:
					if (i > 0 || mult != 0)
						sb.append("[" + (mult + 1) + "]");
					break;
				}
			}
			
			if (i < size() - 1)
				sb.append("/");
		}
		return sb.toString();
	}

	public void readExternal(DataInputStream in, PrototypeFactory pf)
			throws IOException, DeserializationException {
		refLevel = ExtUtil.readInt(in);
		names = (Vector)ExtUtil.read(in, new ExtWrapList(String.class), pf);
		multiplicity = (Vector)ExtUtil.read(in, new ExtWrapList(Integer.class), pf);
		instanceName = (String)ExtUtil.read(in, new ExtWrapNullable(String.class),pf);
		instance = (FormInstance)ExtUtil.read(in, new ExtWrapNullable(FormInstance.class),pf);
		
		//now since predicates are made up of 2 composite data types we have to carefully put it all back to gether again
		Vector<Integer> vi = (Vector) ExtUtil.read(in, new ExtWrapListPoly(), pf);
		for(Integer i : vi)
		{
			Vector<XPathExpression> vx = (Vector) ExtUtil.read(in, new ExtWrapListPoly(), pf);
			predicates.put(i, vx);
		}

	}

	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, refLevel);
		ExtUtil.write(out, new ExtWrapList(names));
		ExtUtil.write(out, new ExtWrapList(multiplicity));
		ExtUtil.write(out, new ExtWrapNullable(instanceName));
		ExtUtil.write(out, new ExtWrapNullable(instance));
		//predicates are complicated because they're a complex data structure, so we have to split them up and then
		//put them back together again
		Vector<Integer> vi = new Vector<Integer>();
		//first the keys of the hash table
		for(Enumeration en = predicates.keys(); en.hasMoreElements(); )
		{
			Integer in = ((Integer)en.nextElement());
			vi.addElement(in);
		}
		ExtUtil.write(out, new ExtWrapListPoly(vi));
		//next the data of the hash table
		for(Enumeration en = predicates.keys(); en.hasMoreElements(); )
		{
			Integer i = ((Integer)en.nextElement());
			ExtUtil.write(out, new ExtWrapListPoly(predicates.get(i)));
		}
	}

	/** Intersect this tree reference with another, returning a new tree reference
	 *  which contains all of the common elements, starting with the root element.
	 *  
	 *  Note that relative references by their nature can't share steps, so intersecting
	 *  any (or by any) relative ref will result in the root ref. Additionally, if the
	 *  two references don't share any steps, the intersection will consist of the root
	 *  reference.
	 *  
	 * @param b The tree reference to intersect
	 * @return The tree reference containing the common basis of this ref and b
	 */
	public TreeReference intersect(TreeReference b) {
		if(!this.isAbsolute() || !b.isAbsolute()) {
			return TreeReference.rootRef();
		}
		if(this.equals(b)) { return this;}
	
	
		TreeReference a;
		//A should always be bigger if one ref is larger than the other
		if(this.size() < b.size()) { a = b.clone() ; b = this.clone();}
		else { a= this.clone(); b = b.clone();}
		
		//Now, trim the refs to the same length.
		int diff = a.size() - b.size();
		for(int i = 0; i < diff; ++i) {
			a.removeLastLevel();
		}
		
		int aSize = a.size();
		//easy, but requires a lot of re-evaluation.
		for(int i = 0 ; i <=  aSize; ++i) {
			if(a.equals(b)) {
				return a;
			} else if(a.size() == 0) {
				return TreeReference.rootRef();
			} else {
				if(!a.removeLastLevel() || !b.removeLastLevel()) {
					//I don't think it should be possible for us to get here, so flip if we do
					throw new RuntimeException("Dug too deply into TreeReference during intersection");
				}
			}
		}
		
		//The only way to get here is if a's size is -1
		throw new RuntimeException("Impossible state");
	}
	
	/**
	 * Returns the subreference of this reference up to the level specified.
	 * 
	 * Used to identify the reference context for a predicate at the same level
	 * 
	 * Must be an absolute reference, otherwise will throw IllegalArgumentException
	 * 
	 * @param i
	 * @return
	 */
	public TreeReference getSubReference(int level) {
		if(!this.isAbsolute()) { throw new IllegalArgumentException("Cannot subreference a non-absolute ref"); }
		
		//Copy construct
		TreeReference ret = new TreeReference();
		ret.setRefLevel(this.refLevel);
		for (int i = 0; i <= level; i++) {
			ret.add(this.getName(i), this.getMultiplicity(i));
		}
		//copy predicates
		for(Enumeration en = predicates.keys(); en.hasMoreElements(); )
		{
			Integer i = ((Integer)en.nextElement());
			ret.addPredicate(i.intValue(), predicates.get(i));
		}
		//copy instances
		if(instanceName != null)
		{
			ret.setInstanceName(instanceName);
		}
		if(instance != null)
		{
			ret.setInstance(instance);
		}
		return ret;

	}
}