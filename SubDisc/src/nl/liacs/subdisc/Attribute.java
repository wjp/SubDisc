package nl.liacs.subdisc;

import java.util.*;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Each {@link Column Column} in a {@link Table Table} is identified by its
 * Attribute. Attributes can be used as PrimaryTarget or SecondaryTarget, but
 * only when the {@link AttributeType AttributeType} of the Attribute is
 * appropriate for the {@link TargetConcept TargetConcept}s
 * {@link TargetType TargetType}. All Attribute constructors and setters ensure
 * that its AttributeType is never <code>null</code> (<code>NOMINAL</code> by
 * default).
 */
public class Attribute implements XMLNodeInterface
{
	// when adding/removing members be sure to update addNodeTo() and loadNode()
	private AttributeType itsType;
	private String itsName;
	private String itsShort;
	private int itsIndex;

	/**
	 * There is only a limited number of AttributeTypes an
	 * {@link Attribute Attribute} can have. The AttributeType <code>enum</code>
	 * contains them all. The
	 * <code>public final String DEFAULT_MISSING_VALUE</code> gives the default
	 * missing value for that AttributeType.
	 */
	public enum AttributeType
	{
		NOMINAL("?"),
		NUMERIC("0.0"),
		ORDINAL("0.0"),
		BINARY("0");

		// used for FileLoading/Column setMissingValue
		private static final TreeSet<String> BOOLEAN_POSITIVES =
			new TreeSet<String>(
					Arrays.asList(new String[] { "1", "true", "t", "yes" }));
		private static final TreeSet<String> BOOLEAN_NEGATIVES =
			new TreeSet<String>(
					Arrays.asList(new String[] { "0", "false", "f", "no" }));

		/*
		 * NOTE if DEFAULT_MISSING_VALUE is changed for NUMERIC/ORDINAL, check
		 * the switch() code for the Column constructor:
		 * public Column(Attribute theAttribute, int theNrRows)
		 */
		/**
		 * The default missing value for each AttributeType. To set a different
		 * missing value use
		 * {@link Column.setNewMissingValue() Column.setNewMissingValue()}.
		 */
		public final String DEFAULT_MISSING_VALUE;

		private AttributeType(String theDefaultMissingValue)
		{
			DEFAULT_MISSING_VALUE = theDefaultMissingValue; 
		}

		/**
		 * Returns the AttributeType corresponding to the <code>String</code>
		 * parameter. If the corresponding AttributeType can not be found, the
		 * default AttributeType NOMINAL is returned. This method is case
		 * insensitive.
		 * 
		 * @param theType the <code>String</code> corresponding to an
		 * AtrtibuteType.
		 * 
		 * @return the AttributeType corresponding to the <code>String</code>
		 * parameter, or AttributeType NOMINAL if no corresponding AttributeType
		 * is found.
		 */
		public static AttributeType getAttributeType(String theType)
		{
			for (AttributeType at : AttributeType.values())
				if (at.toString().equalsIgnoreCase(theType))
					return at;

			/*
			 * theType cannot be resolved to an AttibuteType. Log error and
			 * return default.
			 */
			Log.logCommandLine(
				String.format(
						"'%s' is not a valid AttributeType. Returning NOMINAL.",
						theType));
			return AttributeType.NOMINAL;
		}

		public static boolean isValidBinaryTrueValue(String theBooleanValue)
		{
			return BOOLEAN_POSITIVES.contains(theBooleanValue.toLowerCase());
		}

		public static boolean isValidBinaryFalseValue(String theBooleanValue)
		{
			return BOOLEAN_NEGATIVES.contains(theBooleanValue.toLowerCase());
		}
	}

	//TXT, ARFF
	public Attribute(String theName, String theShort, AttributeType theType, int theIndex)
	{
		itsName =
			(theName == null ? String.valueOf(System.nanoTime()) : theName);	// TODO throw warning
		itsShort = (theShort == null ? "" : theShort);
		itsType = (theType == null ? AttributeType.NOMINAL : theType);
		itsIndex = theIndex;	// TODO should not be < 0
	}

	//MRML
	public Attribute(String theName, String theShort, AttributeType theType)
	{
		itsName =
			(theName == null ? String.valueOf(System.nanoTime()) : theShort);	// TODO throw warning
		itsShort = (theShort == null ? "" : theShort);
		itsType = (theType == null ? AttributeType.NOMINAL : theType);
	}

	/**
	 * Create an Attribute from an XML AttributeNode.
	 */
	public Attribute(Node theAttributeNode)
	{
		if (theAttributeNode == null)
			return;	// TODO throw warning dialog

		NodeList aChildren = theAttributeNode.getChildNodes();
		for (int i = 0, j = aChildren.getLength(); i < j; ++i)
		{
			Node aSetting = aChildren.item(i);
			String aNodeName = aSetting.getNodeName();
			if ("name".equalsIgnoreCase(aNodeName))
				itsName = aSetting.getTextContent();
			else if ("short".equalsIgnoreCase(aNodeName))
				itsShort = aSetting.getTextContent();
			else if ("type".equalsIgnoreCase(aNodeName))
				itsType = setType(aSetting.getTextContent());
			else if ("index".equalsIgnoreCase(aNodeName))
				itsIndex = Integer.parseInt(aSetting.getTextContent());
		}
	}

	public int getIndex() { return itsIndex; }	// is never set for MRML
	public AttributeType getType() { return itsType; }
	public String getName() { return itsName; }
	public String getShort() { return itsShort; }
	public boolean hasShort() { return (!itsShort.isEmpty()); }
	public String getNameAndShort()
	{
		return itsName + (hasShort() ? " (" + getShort() + ")" : "");
	}
	public String getNameOrShort() { return hasShort() ? itsShort : itsName; }
	public String getTypeName() { return itsType.toString().toLowerCase(); }

	public void print()
	{
		Log.logCommandLine(itsIndex + ":" + getNameAndShort() + " " +
							getTypeName());
	}

	public boolean isNominalType() { return itsType == AttributeType.NOMINAL; }
	public boolean isNumericType() { return itsType == AttributeType.NUMERIC; }
	public boolean isOrdinalType() { return itsType == AttributeType.ORDINAL; }
	public boolean isBinaryType() { return itsType == AttributeType.BINARY; }

	/*
	 * TODO this method should be made obsolete.
	 */
	/**
	 * Sets the {@link AttributeType AttributeType} for this Attribute. This is
	 * used for changing the AttributeType of a {@link Column Column}. The
	 * Column is responsible for checking whether its AttributeType can be
	 * changed to this new AttributeType. This method is case insensitive.
	 * 
	 * @param theType the <code>String</code> representation of an valid
	 * AttributeType to set as this Attributes' new AttributeType.
	 * 
	 * @return the new AttributeType, or the default AttributeType.NOMINAL if
	 * the <code>String</code> passed in as a parameter can not be resolved to a
	 *  valid AttributeType.
	 */
	public AttributeType setType(String theType)
	{
		for (AttributeType at : AttributeType.values())
		{
			if (at.toString().equalsIgnoreCase(theType))
			{
				itsType = at;
				break;
			}
		}
		return (itsType == null ? AttributeType.NOMINAL : itsType);
	}

	// TODO this method should replace setType(String theType), and ensure an
	// AttributeType is always set (if itsType == null > AttributeType.NOMINAL).
	/**
	 * Sets the {@link AttributeType AttributeType} for this Attribute. This is
	 * used for changing the AttributeType of a {@link Column Column}. The
	 * Column is responsible for checking whether its AttributeType can be
	 * changed to this new AttributeType.
	 * 
	 * @param theType the AttibuteType to set as this Attributes' new
	 * AttributeType.
	 * 
	 * @return <code>false</code> if the AttributeType passed in as a parameter
	 * is <code>null</code>, <code>true</code> otherwise.
	 */
	public boolean setType(AttributeType theType)
	{
		if (theType != null)
		{
			itsType = theType;
			return true;
		}
		else
			return false;
	}

	/**
	 * Creates an {@link XMLNode XMLNode} representation of this Attribute.
	 * @param theParentNode the Node of which this Node will be a ChildNode.
	 * @return a Node that contains all the information of this Attribute.
	 */
	@Override
	public void addNodeTo(Node theParentNode)
	{
		Node aNode = XMLNode.addNodeTo(theParentNode, "attribute");
		XMLNode.addNodeTo(aNode, "name", itsName);
		XMLNode.addNodeTo(aNode, "short", itsShort);
		XMLNode.addNodeTo(aNode, "type", itsType);
		XMLNode.addNodeTo(aNode, "index", itsIndex);
	}
}
