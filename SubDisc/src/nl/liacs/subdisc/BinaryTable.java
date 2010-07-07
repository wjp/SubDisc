package nl.liacs.subdisc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

public class BinaryTable
{
	private ArrayList<BitSet> itsColumns;
	private int itsNrRecords; //Nr. of examples

	// for LogGamma
	private static final double GAMMA_STP = 2.50662827465;
	private static final double GAMMA_C1 = 76.18009173;
	private static final double GAMMA_C2 = -86.50532033;
	private static final double GAMMA_C3 = 24.01409822;
	private static final double GAMMA_C4 = -1.231739516;
	private static final double GAMMA_C5 = 1.20858003e-3;
	private static final double GAMMA_C6 = -5.36382e-6;


	//From Table
	public BinaryTable(Table theTable, BitSet theColumns)
	{
		int theSize = theColumns.cardinality();
		itsColumns = new ArrayList<BitSet>(theSize);
		itsNrRecords = theTable.getNrRows();

		for (int i=0; i<theTable.getNrColumns(); i++)
			if (theColumns.get(i))
				itsColumns.add(theTable.getBinaryColumn(i));
	}

	//empty
	public BinaryTable()
	{
		itsColumns = new ArrayList<BitSet>();
	}

	//this assumes that theTargets is an ArrayList<BitSet>
	public BinaryTable(ArrayList<BitSet> theTargets, int theNrRecords)
	{
		itsColumns = theTargets;
		itsNrRecords = theNrRecords;
	}

	public BitSet getRow(int theIndex)
	{
		BitSet aBitSet = new BitSet(itsColumns.size());
		for (int i = 0; i < itsColumns.size(); i++)
			aBitSet.set(i, itsColumns.get(i).get(theIndex));

		return aBitSet;
	}

	public BinaryTable selectColumns(ItemSet theItemSet)
	{
		BinaryTable aShallowCopy = new BinaryTable();

		for (int i = 0; i < theItemSet.getDimensions(); i++)
		{
			if (theItemSet.get(i))
				aShallowCopy.addColumn(getColumn(i));
		}

		aShallowCopy.setNrRecords(itsNrRecords);

		return aShallowCopy;
	}

	public BinaryTable selectRows(BitSet theMembers)
	{
		int aNrMembers = theMembers.cardinality();
		ArrayList<BitSet> aNewTargets = new ArrayList<BitSet>(getNrColumns());

		//copy targets
		for (BitSet aColumn : itsColumns)
		{
			BitSet aSmallerTarget = new BitSet(aNrMembers);
			int k=0;
			for (int j=0; j<getNrRecords(); j++)
				if (theMembers.get(j))
				{
					if (aColumn.get(j))
						aSmallerTarget.set(k);
					k++;
				}
			aNewTargets.add(aSmallerTarget);
		}
		BinaryTable aTable = new BinaryTable(aNewTargets, aNrMembers);
		return aTable;
	}

	public CrossCube countCrossCube()
	{
		CrossCube aCrossCube = new CrossCube(itsColumns.size());
		BitSet aBitSet = new BitSet(itsColumns.size());

		for (int i = 0; i < itsNrRecords ; i++)
		{
			aBitSet.clear();
			for (int j = 0; j < itsColumns.size(); j++)
				aBitSet.set(j, itsColumns.get(j).get(i));

			aCrossCube.incrementCount(aBitSet);
		}

		return aCrossCube;
	}

	public double computeBDeuFaster()
	{
		int aDimensions = itsColumns.size();

		// Init crosscube
		int aSize = (int)Math.pow(2, aDimensions);
		int[] aCounts = new int[aSize];
		Arrays.fill(aCounts, 0);
		int aTotalCount = 0;

		// Cache powers
		int[] aPowers = new int[aDimensions];
		for (int j = 0; j < aDimensions; j++)
			aPowers[j] = (int)Math.pow(2, aDimensions-j-1);

		// Fill crosscube
		for (int i = 0; i < itsNrRecords ; i++)
		{
			int anIndex = 0;
			for (int j = 0; j < aDimensions; j++)
				if(itsColumns.get(j).get(i))
					anIndex += aPowers[j];
			aCounts[anIndex]++;
			aTotalCount++;
		}

		// Compute BDeu
		if (aTotalCount == 0)
			return 0;

		double aQuality = 0.0;
		int q_i = aSize / 2;
		double alpha_ijk = 1 / (double) aSize;
		double alpha_ij  = 1 / (double) q_i;
		double LogGam_alpha_ijk = LogGamma(alpha_ijk); //uniform prior BDeu metric
		double LogGam_alpha_ij = LogGamma(alpha_ij);

		for (int j=0; j<q_i; j++)
		{
			double aSum = 0.0;
			double aPost = 0.0;

			//child = 0;
			aPost += LogGamma(alpha_ijk + aCounts[j*2]) - LogGam_alpha_ijk;
			aSum += aCounts[j*2];
			//child = 1;
			aPost += LogGamma(alpha_ijk + aCounts[j*2 + 1]) - LogGam_alpha_ijk;
			aSum += aCounts[j*2 + 1];

			aQuality += LogGam_alpha_ij - LogGamma(alpha_ij + aSum) + aPost;
		}
		return aQuality;
	}

	// TODO duplicate code, same as CrossCube.LogGamma
	public double LogGamma(double x)
	{
		double ser = 1.0 + GAMMA_C1 / x + GAMMA_C2 / (x + 1.0) + GAMMA_C3 / (x + 2.0) + GAMMA_C4 / (x + 3.0) + GAMMA_C5 / (x + 4.0) + GAMMA_C6 / (x + 5.0);
		return (x - 0.5) * Math.log(x + 4.5) - x - 4.5 + Math.log(GAMMA_STP * ser);
	}

	public void print()
	{
		for (int i=0; i<getNrRecords(); i++)
		{
			String aRow = "";
			for (int j=0; j<getNrColumns(); j++)
				aRow += getColumn(j).get(i)?"1":"0";
			Log.logCommandLine(aRow);
		}
	}

	public void setNrRecords(int theNrRecords) { itsNrRecords = theNrRecords; }
	public int getNrRecords() { return itsNrRecords; }
	public int getNrColumns() { return itsColumns.size(); }
	public void addColumn(BitSet theBitSet) { itsColumns.add(theBitSet); }
	public BitSet getColumn(int theIndex) { return itsColumns.get(theIndex);}
	public void removeColumn(BitSet theBitSet) {itsColumns.remove(theBitSet);}
	public void removeColumn(int theIndex) {itsColumns.remove(theIndex);}
	public void setColumn(BitSet theBitSet, int theIndex) {itsColumns.set(theIndex, theBitSet);}
}
