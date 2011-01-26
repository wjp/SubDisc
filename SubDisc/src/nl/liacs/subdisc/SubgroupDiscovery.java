package nl.liacs.subdisc;

import java.util.*;

public class SubgroupDiscovery extends MiningAlgorithm
{
	private final Table itsTable;
	private final int itsMaximumCoverage;	// itsTable.getNrRows()
	private final QualityMeasure itsQualityMeasure;
	private SubgroupSet itsResult;
	private CandidateQueue itsCandidateQueue;
	private int itsCandidateCount;

	//target concept type-specific information, including base models
	private BitSet itsBinaryTarget;		//SINGLE_NOMINAL
	private Column itsNumericTarget;	//SINGLE_NUMERIC
	private Column itsPrimaryColumn;	//DOUBLE_CORRELATION / DOUBLE_REGRESSION
	private Column itsSecondaryColumn;	//DOUBLE_CORRELATION / DOUBLE_REGRESSION
	private CorrelationMeasure itsBaseCM;	//DOUBLE_CORRELATION
	private RegressionMeasure itsBaseRM;	//DOUBLE_REGRESSION
	private BinaryTable itsBinaryTable;	//MULTI_LABEL
	private String[] itsTargets;	//MULTI_LABEL
//	private DAG itsBaseDAG;				//MULTI_LABEL

	//SINGLE_NOMINAL
	public SubgroupDiscovery(SearchParameters theSearchParameters, Table theTable, int theNrPositive)
	{
		super(theSearchParameters);
		itsTable = theTable;
		itsMaximumCoverage = itsTable.getNrRows();
		itsQualityMeasure = new QualityMeasure(itsSearchParameters.getQualityMeasure(), itsMaximumCoverage, theNrPositive);

		TargetConcept aTC = itsSearchParameters.getTargetConcept();
		Condition aCondition = new Condition(aTC.getPrimaryTarget(), Condition.EQUALS);
		aCondition.setValue(aTC.getTargetValue());
		itsBinaryTarget = itsTable.evaluate(aCondition);

		itsResult = new SubgroupSet(itsSearchParameters.getMaximumSubgroups(), itsMaximumCoverage, itsBinaryTarget);
	}

	//SINGLE_NUMERIC
	public SubgroupDiscovery(SearchParameters theSearchParameters, Table theTable, float theAverage)
	{
		super(theSearchParameters);
		itsTable = theTable;
		itsMaximumCoverage = itsTable.getNrRows();
		TargetConcept aTC = itsSearchParameters.getTargetConcept();
		itsNumericTarget = itsTable.getColumn(aTC.getPrimaryTarget());
		NumericDomain aDomain = new NumericDomain(itsNumericTarget);

		itsQualityMeasure = new QualityMeasure(itsSearchParameters.getQualityMeasure(), itsMaximumCoverage,
			aDomain.computeSum(0, itsMaximumCoverage),
			aDomain.computeSumSquaredDeviations(0, itsMaximumCoverage),
			aDomain.computeMedian(0, itsMaximumCoverage),
			aDomain.computeMedianAD(0, itsMaximumCoverage));

		itsResult = new SubgroupSet(itsSearchParameters.getMaximumSubgroups(), itsMaximumCoverage, null); //TODO
	}

	//DOUBLE_CORRELATION and DOUBLE_REGRESSION
	public SubgroupDiscovery(SearchParameters theSearchParameters, Table theTable, boolean isRegression)
	{
		super(theSearchParameters);
		itsTable = theTable;
		itsMaximumCoverage = itsTable.getNrRows();
		itsQualityMeasure = new QualityMeasure(itsSearchParameters.getQualityMeasure(), itsMaximumCoverage, 100); //TODO

		TargetConcept aTC = itsSearchParameters.getTargetConcept();
		itsPrimaryColumn = itsTable.getColumn(aTC.getPrimaryTarget());
		itsSecondaryColumn = itsTable.getColumn(aTC.getSecondaryTarget());
		if (isRegression)
			itsBaseRM = new RegressionMeasure(itsSearchParameters.getQualityMeasure(), itsPrimaryColumn, itsSecondaryColumn, null);
		else
			itsBaseCM = new CorrelationMeasure(itsSearchParameters.getQualityMeasure(), itsPrimaryColumn, itsSecondaryColumn);

		itsResult = new SubgroupSet(itsSearchParameters.getMaximumSubgroups());
	}

	//MULTI_LABEL
	public SubgroupDiscovery(SearchParameters theSearchParameters, Table theTable)
	{
		super(theSearchParameters);
		itsTable = theTable;
		itsMaximumCoverage = itsTable.getNrRows();

		//compute base model
		ArrayList<Attribute> anAttributes = itsSearchParameters.getTargetConcept().getMultiTargets();
		itsBinaryTable = new BinaryTable(itsTable, anAttributes);
		itsTargets = new String[anAttributes.size()];
		for (int i = 0, j = anAttributes.size(); i < j; ++i)
			itsTargets[i] = anAttributes.get(i).getName();
		Bayesian aBayesian = new Bayesian(itsBinaryTable, itsTargets);
		aBayesian.climb();
		//TODO fix alpha, beta
		itsQualityMeasure = new QualityMeasure(itsSearchParameters.getQualityMeasure(),
												aBayesian.getDAG(),
												itsMaximumCoverage,
												itsSearchParameters.getAlpha(),
												itsSearchParameters.getBeta());

		itsResult = new SubgroupSet(itsSearchParameters.getMaximumSubgroups());
	}

	public void Mine(long theBeginTime)
	{
		//make subgroup to start with, containing all elements
		Subgroup aStart = new Subgroup(0.0, itsMaximumCoverage, 0, itsResult);
		BitSet aBitSet = new BitSet(itsMaximumCoverage);
		aBitSet.set(0,itsMaximumCoverage);
		aStart.setMembers(aBitSet);

		itsCandidateQueue = new CandidateQueue(itsSearchParameters, new Candidate(aStart, 0.0f));
		itsCandidateCount = 0;

		int aSearchDepth = itsSearchParameters.getSearchDepth();
		long theEndTime = theBeginTime + (long)(itsSearchParameters.getMaximumTime()*60*1000);
		// TODO can itsCandidateQueue ever become null?
		while ((itsCandidateQueue != null && itsCandidateQueue.size() > 0 ) && (System.currentTimeMillis() <= theEndTime))
		{
			Candidate aCandidate = itsCandidateQueue.removeFirst(); // take off first Candidate from Queue
			Subgroup aSubgroup = aCandidate.getSubgroup();

			if (aSubgroup.getDepth() < aSearchDepth)
			{
				RefinementList aRefinementList = new RefinementList(aSubgroup, itsTable, itsSearchParameters.getTargetConcept());

				for (int i = 0, j = aRefinementList.size(); i < j; i++)
				{
					if (System.currentTimeMillis() > theEndTime)
						break;

					Refinement aRefinement = aRefinementList.get(i);
					if (aRefinement.getCondition().getAttribute().isNumericType())
						evaluateNumericRefinements(theBeginTime, aSubgroup, aRefinement);
					else
						evaluateNominalBinaryRefinements(theBeginTime, aSubgroup, aRefinement);
				}
			}
		}
		Log.logCommandLine("number of candidates: " + itsCandidateCount);
		Log.logCommandLine("number of subgroups: " + getNumberOfSubgroups());

		itsResult.setIDs(); //assign 1 to n to subgroups, for future reference in subsets
	}
/*
	public void Mine(long theBeginTime)
	{
		//make subgroup to start with, containing all elements
		Subgroup aStart = new Subgroup(0.0, itsMaximumCoverage, 0, itsResult);
		BitSet aBitSet = new BitSet(itsMaximumCoverage);
		aBitSet.set(0,itsMaximumCoverage);
		aStart.setMembers(aBitSet);

		Mine(theBeginTime, aStart);
		itsResult.setIDs(); //assign 1 to n to subgroups, for future reference in subsets
	}

	//what Exception can be thrown, requiring this to be in a try block?
	public void Mine(long theBeginTime, Subgroup theStart)
	{
		try
		{
			Candidate aRootCandidate = new Candidate(theStart, 0.0f);
			itsCandidateQueue = new CandidateQueue(itsSearchParameters.getSearchStrategy(),
													itsSearchParameters.getSearchStrategyWidth(),
													aRootCandidate);
			itsCandidateCount = 0;

			int aSearchDepth = itsSearchParameters.getSearchDepth();
			long theEndTime = theBeginTime + (long)(itsSearchParameters.getMaximumTime()*60*1000);
			while ((itsCandidateQueue != null && itsCandidateQueue.size() > 0 ) && (System.currentTimeMillis() <= theEndTime))
			{
				Candidate aCandidate = itsCandidateQueue.removeFirst(); // take off first Candidate from Queue
				Subgroup aSubgroup = aCandidate.getSubgroup();

				if (aSubgroup.getDepth() < aSearchDepth)
				{
					RefinementList aRefinementList = new RefinementList(aSubgroup, itsTable, itsSearchParameters.getTargetConcept());

					for (int i = 0, j = aRefinementList.size(); i < j; i++)
					{
						if (System.currentTimeMillis() > theEndTime)
							break;

						Refinement aRefinement = aRefinementList.get(i);
						if (aRefinement.getCondition().getAttribute().isNumericType())
							evaluateNumericRefinements(theBeginTime, aSubgroup, aRefinement);
						else
							evaluateNominalBinaryRefinements(theBeginTime, aSubgroup, aRefinement);
					}
				}
			}
			Log.logCommandLine("number of candidates: " + itsCandidateCount);
			Log.logCommandLine("number of subgroups: " + getNumberOfSubgroups());
		}
		catch (Exception e)
		{
			ErrorWindow aWindow = new ErrorWindow(e);
			aWindow.setLocation(200, 200);
			aWindow.setVisible(true);
			e.printStackTrace();
		}
	}
*/
	private void evaluateNumericRefinements(long theBeginTime, Subgroup theSubgroup, Refinement theRefinement)
	{
		int anAttributeIndex = theRefinement.getCondition().getAttribute().getIndex();
		int aMinimumCoverage = itsSearchParameters.getMinimumCoverage();
		float aQualityMeasureMinimum = itsSearchParameters.getQualityMeasureMinimum();

		switch (itsSearchParameters.getNumericStrategy())
		{
			case NUMERIC_ALL :
			{
				float[] aSplitPoints = itsTable.getUniqueNumericDomain(anAttributeIndex, theSubgroup.getMembers());
				for (float aSplit : aSplitPoints)
				{
					String aConditionValue = Float.toString(aSplit);
					Subgroup aNewSubgroup = theRefinement.getRefinedSubgroup(aConditionValue);
					BitSet aMembers = itsTable.evaluate(aNewSubgroup.getConditions());
					aNewSubgroup.setMembers(aMembers);

					if (aNewSubgroup.getCoverage() >= aMinimumCoverage)
					{
						Log.logCommandLine("candidate " + aNewSubgroup.getConditions() + " size: " + aNewSubgroup.getCoverage());
						float aQuality = evaluateCandidate(aNewSubgroup);
						aNewSubgroup.setMeasureValue(aQuality);
						if (aQuality > aQualityMeasureMinimum)
							itsResult.add(aNewSubgroup);
						itsCandidateQueue.add(new Candidate(aNewSubgroup, aQuality));
						Log.logCommandLine("  subgroup nr. " + itsCandidateCount + "; quality " + aNewSubgroup.getMeasureValue());
					}
					itsCandidateCount++;
				}
				break;
			}
			case NUMERIC_BINS :
			{
				int aNrSplitPoints = itsSearchParameters.getNrBins() - 1;  //this is the crucial translation from nr bins to nr splitpoint
				float[] aSplitPoints = itsTable.getSplitPoints(anAttributeIndex, theSubgroup.getMembers(), aNrSplitPoints);
				boolean first = true;
				for (int j=0; j<aNrSplitPoints; j++)
				{
					if (first || aSplitPoints[j] != aSplitPoints[j-1])
					{
						String aConditionValue = Float.toString(aSplitPoints[j]);
						Subgroup aNewSubgroup = theRefinement.getRefinedSubgroup(aConditionValue);
						BitSet aMembers = itsTable.evaluate(aNewSubgroup.getConditions());
						aNewSubgroup.setMembers(aMembers);

						if (aNewSubgroup.getCoverage() >= aMinimumCoverage)
						{
							Log.logCommandLine("candidate " + aNewSubgroup.getConditions() + " size: " + aNewSubgroup.getCoverage());
							float aQuality = evaluateCandidate(aNewSubgroup);
							aNewSubgroup.setMeasureValue(aQuality);
							if (aQuality > aQualityMeasureMinimum)
								itsResult.add(aNewSubgroup);
							itsCandidateQueue.add(new Candidate(aNewSubgroup, aQuality));
							Log.logCommandLine("  subgroup nr. " + itsCandidateCount + "; quality " + aNewSubgroup.getMeasureValue());
						}
						itsCandidateCount++;
					}
					first = false;
				}
				break;
			}
			case NUMERIC_BEST :
			{
				float[] aSplitPoints = itsTable.getUniqueNumericDomain(anAttributeIndex, theSubgroup.getMembers());
				float aMax = Float.NEGATIVE_INFINITY;
				float aBest = aSplitPoints[0];
				Subgroup aBestSubgroup = null;
				for (float aSplit : aSplitPoints)
				{
					String aConditionValue = Float.toString(aSplit);
					Subgroup aNewSubgroup = theRefinement.getRefinedSubgroup(aConditionValue);
					BitSet aMembers = itsTable.evaluate(aNewSubgroup.getConditions());
					aNewSubgroup.setMembers(aMembers);

					if (aNewSubgroup.getCoverage() >= aMinimumCoverage)
					{
						float aQuality = evaluateCandidate(aNewSubgroup);
						aNewSubgroup.setMeasureValue(aQuality);
						if (aQuality > aMax)
						{
							aMax = aQuality;
							aBest = aSplit;
							aBestSubgroup = aNewSubgroup;
						}
					}
				}

				//add best
				if (aBestSubgroup!=null) //at least one threshold found that has enough quality and coverage
				{
					Log.logCommandLine("candidate " + aBestSubgroup.getConditions() + " size: " + aBestSubgroup.getCoverage());
					if (aMax > aQualityMeasureMinimum)
						itsResult.add(aBestSubgroup);
					itsCandidateQueue.add(new Candidate(aBestSubgroup, aMax));
					Log.logCommandLine("  subgroup nr. " + itsCandidateCount + "; quality " + aMax);
					itsCandidateCount++;
				}
				break;
			}
		}
	}

	private void evaluateNominalBinaryRefinements(long theBeginTime, Subgroup theSubgroup, Refinement theRefinement)
	{
		Attribute anAttribute = theRefinement.getCondition().getAttribute();
		int anAttributeIndex = anAttribute.getIndex();
		TreeSet<String> aDomain = itsTable.getDomain(anAttributeIndex);
		int aMinimumCoverage = itsSearchParameters.getMinimumCoverage();
		float aQualityMeasureMinimum = itsSearchParameters.getQualityMeasureMinimum();

		for (String aConditionValue : aDomain)
		{
			Subgroup aNewSubgroup = theRefinement.getRefinedSubgroup(aConditionValue);
			BitSet aMembers = itsTable.evaluate(aNewSubgroup.getConditions());
			aNewSubgroup.setMembers(aMembers);

			if (aNewSubgroup.getCoverage() >= aMinimumCoverage)
			{
				Log.logCommandLine("candidate " + aNewSubgroup.getConditions() + " size: " + aNewSubgroup.getCoverage());
				float aQuality = evaluateCandidate(aNewSubgroup);
				aNewSubgroup.setMeasureValue(aQuality);
				if (aQuality > aQualityMeasureMinimum)
					itsResult.add(aNewSubgroup);
				itsCandidateQueue.add(new Candidate(aNewSubgroup, aQuality));
				Log.logCommandLine("  subgroup nr. " + itsCandidateCount + "; quality " + aNewSubgroup.getMeasureValue());
			}
			itsCandidateCount++;
		}
	}

	private float evaluateCandidate(Subgroup theSubgroup)
	{
		float aQuality = 0.0f;

		switch (itsSearchParameters.getTargetType())
		{
			case SINGLE_NOMINAL :
			{
				BitSet aTarget = (BitSet)itsBinaryTarget.clone();
				aTarget.and(theSubgroup.getMembers());
				int aCountHeadBody = aTarget.cardinality();
				aQuality = itsQualityMeasure.calculate(aCountHeadBody, theSubgroup.getCoverage());
				break;
			}
			case SINGLE_NUMERIC :
			{
				NumericDomain aDomain = new NumericDomain(itsNumericTarget, theSubgroup.getMembers());
				aQuality = itsQualityMeasure.calculate(theSubgroup.getCoverage(),
					aDomain.computeSum(0, theSubgroup.getCoverage()),
					aDomain.computeSumSquaredDeviations(0, theSubgroup.getCoverage()),
					aDomain.computeMedian(0, theSubgroup.getCoverage()),
					aDomain.computeMedianAD(0, theSubgroup.getCoverage()),
					null); //TODO fix this parameter. only used by X2
				break;
			}
			case DOUBLE_REGRESSION :
			{
				RegressionMeasure aRM = new RegressionMeasure(itsBaseRM);
				for (int i = 0; i < itsMaximumCoverage; i++)
					if (theSubgroup.getMembers().get(i))
						aRM.addObservation(itsPrimaryColumn.getFloat(i), itsSecondaryColumn.getFloat(i));

				aQuality = (float) aRM.getEvaluationMeasureValue();
				break;
			}
			case DOUBLE_CORRELATION :
			{
				CorrelationMeasure aCM = new CorrelationMeasure(itsBaseCM);
				for (int i = 0; i < itsMaximumCoverage; i++)
					if (theSubgroup.getMembers().get(i))
						aCM.addObservation(itsPrimaryColumn.getFloat(i), itsSecondaryColumn.getFloat(i));

				aQuality = (float) aCM.getEvaluationMeasureValue();
				break;
			}
			case MULTI_LABEL :
			{
				aQuality = weightedEntropyEditDistance(theSubgroup); //also stores DAG in Subgroup
				break;
			}
			default : break;
		}
		return aQuality;
	}

	private float weightedEntropyEditDistance(Subgroup theSubgroup)
	{
		BinaryTable aBinaryTable = itsBinaryTable.selectRows(theSubgroup.getMembers());
		Bayesian aBayesian = new Bayesian(aBinaryTable, itsTargets);
		aBayesian.climb(); //induce DAG
		DAG aDAG = aBayesian.getDAG();
		theSubgroup.setDAG(aDAG); //store DAG with subgroup for later use
//		return itsQualityMeasure.calculateEDIT_DISTANCE(theSubgroup);
		return itsQualityMeasure.calculate(theSubgroup);
	}

	public int getNumberOfSubgroups() { return itsResult.size(); }
	public SubgroupSet getResult() { return itsResult; }
	public BitSet getBinaryTarget() { return (BitSet)itsBinaryTarget.clone(); }
	public QualityMeasure getQualityMeasure() { return itsQualityMeasure; }
	public SearchParameters getSearchParameters() { return itsSearchParameters; }
}
