package nl.liacs.subdisc;

import java.util.*;

import nl.liacs.subdisc.gui.*;

/**
 * Functionality related to the statistical validation of subgroups.
 */
public class Validation
{
	private SearchParameters itsSearchParameters;
	private TargetConcept itsTargetConcept;
	private QualityMeasure itsQualityMeasure;
	private Table itsTable;

	public Validation(SearchParameters theSearchParameters, Table theTable, QualityMeasure theQualityMeasure)
	{
		itsSearchParameters = theSearchParameters;
		itsTargetConcept = theSearchParameters.getTargetConcept();
		itsTable = theTable;
		itsQualityMeasure = theQualityMeasure;
	}

	public double[] getQualities(String[] theSetup)
	{
		if (!RandomQualitiesWindow.isValidRandomQualitiesSetup(theSetup))
			return null;

		String aMethod = theSetup[0];
		int aNrRepetitions = Integer.parseInt(theSetup[1]);

		if (RandomQualitiesWindow.RANDOM_SUBSETS.equals(aMethod))
			return randomSubgroups(aNrRepetitions);
		else if (RandomQualitiesWindow.RANDOM_DESCRIPTIONS.equals(aMethod))
			return randomConditions(aNrRepetitions);
		else if (RandomQualitiesWindow.SWAP_RANDOMIZATION.equals(aMethod))
			return swapRandomization(aNrRepetitions);

		return null;
	}

	private double[] randomSubgroups(int theNrRepetitions)
	{
		double[] aQualities = new double[theNrRepetitions];
		Random aRandom = new Random(System.currentTimeMillis());
		int aNrRows  =itsTable.getNrRows();
		int aMinimumCoverage = itsSearchParameters.getMinimumCoverage();
		int aSubgroupSize;
		TargetType aTargetType = itsTargetConcept.getTargetType();

		switch (aTargetType)
		{
			case SINGLE_NOMINAL :
			{
				Column aTarget = itsTargetConcept.getPrimaryTarget();
				Condition aCondition = new Condition(aTarget, Operator.EQUALS);
				aCondition.setValue(itsTargetConcept.getTargetValue());
				//BitSet aBinaryTarget = itsTable.evaluate(aCondition);
				BitSet aBinaryTarget = aTarget.evaluate(aCondition);

				for (int i=0; i<theNrRepetitions; i++)
				{
					do
						aSubgroupSize = (int) (aRandom.nextDouble() * aNrRows);
					while (aSubgroupSize < aMinimumCoverage  || aSubgroupSize==aNrRows);
					Subgroup aSubgroup = itsTable.getRandomSubgroup(aSubgroupSize);

					BitSet aColumnTarget = (BitSet) aBinaryTarget.clone();
					aColumnTarget.and(aSubgroup.getMembers());
					int aCountHeadBody = aColumnTarget.cardinality();
					aQualities[i] = itsQualityMeasure.calculate(aCountHeadBody, aSubgroup.getCoverage());
				}
				break;
			}
			case SINGLE_NUMERIC : // TODO implement!
			{
				Column aTarget = itsTargetConcept.getPrimaryTarget();
				Condition aCondition = new Condition(aTarget, Operator.EQUALS);
				aCondition.setValue(itsTargetConcept.getTargetValue());
				//BitSet aBinaryTarget = itsTable.evaluate(aCondition);
				BitSet aBinaryTarget = aTarget.evaluate(aCondition);

				for (int i=0; i<theNrRepetitions; i++)
				{
					do
						aSubgroupSize = (int) (aRandom.nextDouble() * aNrRows);
					while (aSubgroupSize < aMinimumCoverage  || aSubgroupSize==aNrRows);
					Subgroup aSubgroup = itsTable.getRandomSubgroup(aSubgroupSize);

					BitSet aColumnTarget = (BitSet) aBinaryTarget.clone();
					aColumnTarget.and(aSubgroup.getMembers());
					int aCountHeadBody = aColumnTarget.cardinality();
					aQualities[i] = itsQualityMeasure.calculate(aCountHeadBody, aSubgroup.getCoverage());
				}
				break;
			}
			case SINGLE_ORDINAL:
			{
				throw newAssertionError("randomSubgroups", aTargetType);
			}
			case DOUBLE_REGRESSION :
				//TODO implement
				/*
				 * FIXME
				 * there is no break; here
				 * this causes a fall-through from
				 * DOUBLE_REGRESSION to DOUBLE_CORRELATION so
				 * the code for DOUBLE_CORRELATION is also run
				 * for DOUBLE_REGRESSION and their result is the
				 * same
				 * is this deliberate?
				 */
			case DOUBLE_CORRELATION :
			{
				Column aPrimaryColumn = itsTargetConcept.getPrimaryTarget();
				Column aSecondaryColumn = itsTargetConcept.getSecondaryTarget();
				CorrelationMeasure itsBaseCM =
					new CorrelationMeasure(itsSearchParameters.getQualityMeasure(), aPrimaryColumn, aSecondaryColumn);

				for (int i=0; i<theNrRepetitions; i++)
				{
					do
						aSubgroupSize = (int) (aRandom.nextDouble() * aNrRows);
					while (aSubgroupSize < aMinimumCoverage  || aSubgroupSize==aNrRows);
					Subgroup aSubgroup = itsTable.getRandomSubgroup(aSubgroupSize);

					CorrelationMeasure aCM = new CorrelationMeasure(itsBaseCM);

					// FIXME getMembers() is expensive now, take out of loop
					for (int j=0; j<aNrRows; j++)
						if (aSubgroup.getMembers().get(j))
							aCM.addObservation(aPrimaryColumn.getFloat(j), aSecondaryColumn.getFloat(j));

					aQualities[i] = aCM.getEvaluationMeasureValue();
				}
				break;
			}
			case MULTI_LABEL :
			{
				//base model
				BinaryTable aBaseTable = new BinaryTable(itsTable, itsTargetConcept.getMultiTargets());
				Bayesian aBayesian = new Bayesian(aBaseTable);
				aBayesian.climb();

				for (int i=0; i<theNrRepetitions; i++)
				{
					do
						aSubgroupSize = (int) (aRandom.nextDouble() * aNrRows);
					while (aSubgroupSize < aMinimumCoverage  || aSubgroupSize==aNrRows);
					Subgroup aSubgroup = itsTable.getRandomSubgroup(aSubgroupSize);

					// build model
					BinaryTable aBinaryTable = aBaseTable.selectRows(aSubgroup.getMembers());
					aBayesian = new Bayesian(aBinaryTable);
					aBayesian.climb();
					aSubgroup.setDAG(aBayesian.getDAG()); // store DAG with subgroup for later use

					aQualities[i] = itsQualityMeasure.calculate(aSubgroup);
				}
				break;
			}
			case MULTI_BINARY_CLASSIFICATION :
			{
				throw newAssertionError("randomSubgroups", aTargetType);
			}
			default :
			{
				throw newAssertionError("randomSubgroups", aTargetType);
			}
		}

		return aQualities; //return the qualities of the random sample
	}

	/**
	* Generates a set of random descriptions
	* ({@link ConditionList ConditionLists}) of {@link Subgroup Subgroups},
	* by randomly combining random {@link Condition Conditions} on
	* attributes in the {@link Table}.
	* The random descriptions adhere to the {@link SearchParameters}.
	* For each of the subgroups related to the random conditions, the
	* quality is computed.
	* 
	* @return the computed qualities.
	*/
	private double[] randomConditions(int theNrRepetitions)
	{
		double[] aQualities = new double[theNrRepetitions];
		Random aRandom = new Random(System.currentTimeMillis());
		int aDepth = itsSearchParameters.getSearchDepth();
		int aMinimumCoverage = itsSearchParameters.getMinimumCoverage();
		int aNrRows = itsTable.getNrRows();
		TargetType aTargetType = itsTargetConcept.getTargetType();

		switch (aTargetType)
		{
			case SINGLE_NOMINAL :
			{
				Column aTarget = itsTargetConcept.getPrimaryTarget();
				Condition aCondition = new Condition(aTarget, Operator.EQUALS);
				aCondition.setValue(itsTargetConcept.getTargetValue());
				//BitSet aBinaryTarget = itsTable.evaluate(aCondition);
				BitSet aBinaryTarget = aTarget.evaluate(aCondition);

				for (int i=0; i<theNrRepetitions; i++)
				{
					ConditionList aCL;
					BitSet aMembers;
					do
					{
						aCL = getRandomConditionList(aDepth, aRandom);
						aMembers = itsTable.evaluate(aCL);
					}
					while (aMembers.cardinality() < aMinimumCoverage || aMembers.cardinality()==aNrRows);
//					Log.logCommandLine(aCL.toString());
					Subgroup aSubgroup = new Subgroup(aCL, aMembers, null);

					BitSet aColumnTarget = (BitSet) aBinaryTarget.clone();
					aColumnTarget.and(aSubgroup.getMembers());
					int aCountHeadBody = aColumnTarget.cardinality();
					aQualities[i] = itsQualityMeasure.calculate(aCountHeadBody, aSubgroup.getCoverage());
				}
				break;
			}
			case SINGLE_NUMERIC:
			{
				throw newAssertionError("randomConditions", aTargetType);
			}
			case SINGLE_ORDINAL:
			{
				throw newAssertionError("randomConditions", aTargetType);
			}
			case DOUBLE_REGRESSION :
				//TODO implement
				/*
				 * FIXME
				 * there is no break; here
				 * this causes a fall-through from
				 * DOUBLE_REGRESSION to DOUBLE_CORRELATION so
				 * the code for DOUBLE_CORRELATION is also run
				 * for DOUBLE_REGRESSION and their result is the
				 * same
				 * is this deliberate?
				 */
			case DOUBLE_CORRELATION :
			{
				Column aPrimaryColumn = itsTargetConcept.getPrimaryTarget();
				Column aSecondaryColumn = itsTargetConcept.getSecondaryTarget();
				CorrelationMeasure itsBaseCM =
					new CorrelationMeasure(itsSearchParameters.getQualityMeasure(), aPrimaryColumn, aSecondaryColumn);

				for (int i=0; i<theNrRepetitions; i++)
				{
					ConditionList aCL;
					BitSet aMembers;
					do
					{
						aCL = getRandomConditionList(aDepth, aRandom);
						aMembers = itsTable.evaluate(aCL);
					}
					while (aMembers.cardinality() < aMinimumCoverage || aMembers.cardinality()==aNrRows);
					Log.logCommandLine(aCL.toString());
					Subgroup aSubgroup = new Subgroup(aCL, aMembers, null);

					CorrelationMeasure aCM = new CorrelationMeasure(itsBaseCM);

					// FIXME getMembers() is expensive now, take out of loop
					for (int j=0; j<aNrRows; j++)
						if (aSubgroup.getMembers().get(j))
							aCM.addObservation(aPrimaryColumn.getFloat(j), aSecondaryColumn.getFloat(j));

					aQualities[i] = aCM.getEvaluationMeasureValue();
				}
				break;
			}
			case MULTI_LABEL :
			{
				// base model
				BinaryTable aBaseTable = new BinaryTable(itsTable, itsTargetConcept.getMultiTargets());
				Bayesian aBayesian = new Bayesian(aBaseTable);
				aBayesian.climb();

				for (int i = 0; i < theNrRepetitions; i++) // random conditions
				{
					ConditionList aCL;
					BitSet aMembers;
					do
					{
						aCL = getRandomConditionList(aDepth, aRandom);
						aMembers = itsTable.evaluate(aCL);
					}
					while (aMembers.cardinality() < aMinimumCoverage || aMembers.cardinality()==aNrRows);
					Log.logCommandLine(aCL.toString());
					Subgroup aSubgroup = new Subgroup(aCL, aMembers, null);

					// build model
					BinaryTable aBinaryTable = aBaseTable.selectRows(aMembers);
					aBayesian = new Bayesian(aBinaryTable);
					aBayesian.climb();
					aSubgroup.setDAG(aBayesian.getDAG()); // store DAG with subgroup for later use

					aQualities[i] = itsQualityMeasure.calculate(aSubgroup);
					Log.logCommandLine((i + 1) + "," + aSubgroup.getCoverage() + "," + aQualities[i]);
				}
				break;
			}
			case MULTI_BINARY_CLASSIFICATION :
			{
				throw newAssertionError("randomConditions", aTargetType);
			}
			default :
			{
				throw newAssertionError("randomConditions", aTargetType);
			}
		}

		return aQualities; //return the qualities of the random sample
	}

	private AssertionError newAssertionError(String theMethod, TargetType theTargetType)
	{
		return new AssertionError(new  StringBuilder(64).append(this.getClass().getSimpleName())
								.append(".")
								.append(theMethod)
								.append("(): ")
								.append(theTargetType.getClass().getSimpleName())
								.append(" '")
								.append(theTargetType)
								.append("' not implemented").toString());
	}

	/**
	 * Swap randomizes the original {@link Table} and restores it to the
	 * original state afterwards.
	 *
	 * @param theNrRepetitions the number of times to perform a permutation
	 * of the {@link TargetConcept}.
	 *
	 * @return an array holding the qualities of the best scoring
	 * {@link Subgroup Subgroup} of each permutation.
	 */
	private double[] swapRandomization(int theNrRepetitions)
	{
		// Memorize COMMANDLINELOG setting
		boolean aCOMMANDLINELOGmem = Log.COMMANDLINELOG;
		double[] aQualities = new double[theNrRepetitions];

		// Always back up and restore columns that will be swap randomized.
		switch(itsTargetConcept.getTargetType())
		{
			case SINGLE_NOMINAL :
			{
				// back up column that will be swap randomized
				Column aPrimaryCopy = itsTargetConcept.getPrimaryTarget().copy();
				int aPositiveCount =
					//itsTable.countValues(itsTargetConcept.getPrimaryTarget().getIndex(), itsTargetConcept.getTargetValue());
					itsTargetConcept.getPrimaryTarget().countValues(itsTargetConcept.getTargetValue());

				// generate swap randomized random results
				for (int i = 0, j = theNrRepetitions; i < j; ++i)
				{
					// swapRandomization should be performed before creating new SubgroupDiscovery
					itsTable.swapRandomizeTarget(itsTargetConcept);
					i = runSRSD(new SubgroupDiscovery(itsSearchParameters, itsTable, aPositiveCount, null), aQualities,	i);
				}

				// restore column that was swap randomized
				itsTargetConcept.setPrimaryTarget(aPrimaryCopy);
				itsTable.getColumns().set(aPrimaryCopy.getIndex(), aPrimaryCopy);

				break;
			}
			case SINGLE_NUMERIC :
			{
				// back up column that will be swap randomized
				Column aPrimaryCopy = itsTargetConcept.getPrimaryTarget().copy();
				float aTargetAverage = itsTargetConcept.getPrimaryTarget().getAverage();

				// generate swap randomized random results
				for (int i = 0, j = theNrRepetitions; i < j; ++i)
				{
					// swapRandomization should be performed before creating new SubgroupDiscovery
					itsTable.swapRandomizeTarget(itsTargetConcept);
					SubgroupDiscovery anSD = new SubgroupDiscovery(itsSearchParameters, itsTable, aTargetAverage, null);
					i = runSRSD(anSD, aQualities, i);
				}

				// restore column that was swap randomized
				itsTargetConcept.setPrimaryTarget(aPrimaryCopy);
				itsTable.getColumns().set(aPrimaryCopy.getIndex(), aPrimaryCopy);

				break;
			}
			case DOUBLE_CORRELATION :
			{
				// back up columns that will be swap randomized
				Column aPrimaryCopy = itsTargetConcept.getPrimaryTarget().copy();
				Column aSecondaryCopy = itsTargetConcept.getSecondaryTarget().copy();

				// generate swap randomized random results
				for (int i = 0, j = theNrRepetitions; i < j; ++i)
				{
					// swapRandomization should be performed before creating new SubgroupDiscovery
					itsTable.swapRandomizeTarget(itsTargetConcept);
					i = runSRSD(new SubgroupDiscovery(itsSearchParameters, itsTable, false, null), aQualities, i);
				}

				// restore columns that were swap randomized
				itsTargetConcept.setPrimaryTarget(aPrimaryCopy);
				itsTable.getColumns().set(aPrimaryCopy.getIndex(), aPrimaryCopy);
				itsTargetConcept.setSecondaryTarget(aSecondaryCopy);
				itsTable.getColumns().set(aSecondaryCopy.getIndex(), aSecondaryCopy);

				break;
			}
			case DOUBLE_REGRESSION :
			{
				// back up columns that will be swap randomized
				Column aPrimaryCopy = itsTargetConcept.getPrimaryTarget().copy();
				Column aSecondaryCopy = itsTargetConcept.getSecondaryTarget().copy();

				// generate swap randomized random results
				for (int i = 0, j = theNrRepetitions; i < j; ++i)
				{
					// swapRandomization should be performed before creating new SubgroupDiscovery
					itsTable.swapRandomizeTarget(itsTargetConcept);
					i = runSRSD(new SubgroupDiscovery(itsSearchParameters, itsTable, true, null),	aQualities,	i);
				}

				// restore columns that were swap randomized
				itsTargetConcept.setPrimaryTarget(aPrimaryCopy);
				itsTable.getColumns().set(aPrimaryCopy.getIndex(), aPrimaryCopy);
				itsTargetConcept.setSecondaryTarget(aSecondaryCopy);
				itsTable.getColumns().set(aSecondaryCopy.getIndex(), aSecondaryCopy);

				break;
			}
			case MULTI_LABEL :
			{
				// back up columns that will be swap randomized
				List<Column> aMultiCopy =
					new ArrayList<Column>(itsTargetConcept.getMultiTargets().size());
				for (Column c : itsTargetConcept.getMultiTargets())
					aMultiCopy.add(c.copy());

				// generate swap randomized random results
				for (int i = 0, j = theNrRepetitions; i < j; ++i)
				{
					// swapRandomization should be performed before creating new SubgroupDiscovery
					itsTable.swapRandomizeTarget(itsTargetConcept);
					i = runSRSD(new SubgroupDiscovery(itsSearchParameters, itsTable, null),	aQualities,	i);
				}

				// restore columns that were swap randomized
				itsTargetConcept.setMultiTargets(aMultiCopy);
				for (Column c : aMultiCopy)
					itsTable.getColumns().set(c.getIndex(), c);

				break;
			}
			default : break;
		}

		Log.COMMANDLINELOG = aCOMMANDLINELOGmem;
		return aQualities;
	}

	/*
	 * NOTE for the first result (i = 0) to be not equal to the original mining
	 * result the calling function should run:
	 * itsTable.swapRandomizeTarget(itsTargetConcept);
	 * before creating the new theSubgroupDiscovery, and calling this function.
	 */
	private int runSRSD(SubgroupDiscovery theSubgroupDiscovery, double[] theQualities, int theRepetition)
	{
		Log.COMMANDLINELOG = false;
		theSubgroupDiscovery.mine(System.currentTimeMillis());
		Log.COMMANDLINELOG = true;
		SubgroupSet aSubgroupSet = theSubgroupDiscovery.getResult();
		if (aSubgroupSet.size() == 0)
			--theRepetition; // if no subgroups are found, try again.
		else
		{
			theQualities[theRepetition] = aSubgroupSet.getBestScore();
			Log.logCommandLine((theRepetition + 1) + "," + theQualities[theRepetition]);
		}

		return theRepetition;
	}

	private double performRegressionTest(double[] theQualities, int theK, SubgroupSet theSubgroupSet)
	{
		//extract average quality
		double aTopKQuality = 0.0;
		for (Subgroup aSubgroup : theSubgroupSet)
			aTopKQuality += aSubgroup.getMeasureValue();
		aTopKQuality /= theK;

		// make deep copy of double array
		//public static double[] copyOf(double[] original, int newLength)
		int theNrRandomSubgroups = theQualities.length;
		double[] aCopy = Arrays.copyOf(theQualities, theQualities.length);

		// rescale all qualities between 0 and 1
		// also compute some necessary statistics
		Arrays.sort(aCopy);

		double aMin = Math.min(aCopy[0], aTopKQuality);
		double aMax = Math.max(aCopy[theNrRandomSubgroups-1], aTopKQuality);
		double xBar = 0.5; // given our scaling this always holds
		double yBar = 0.0; // initial value
		for (int i=0; i<theNrRandomSubgroups; i++)
		{
			aCopy[i] = (aCopy[i]-aMin)/(aMax-aMin);
			yBar += aCopy[i];
		}
		aTopKQuality = (aTopKQuality-aMin)/(aMax-aMin);
		yBar = (yBar+aTopKQuality)/((double) theNrRandomSubgroups + 1);

		// perform least squares linear regression on equidistant x-values and computed y-values
		double xxBar = 0.25; // initial value: this equals the square of (the x-value of our subgroup minus xbar)
		double xyBar = 0.5 * (aTopKQuality - yBar);
		double[] anXs = new double[theNrRandomSubgroups];
		for (int i=0; i<theNrRandomSubgroups; i++)
		{
			anXs[i] = ((double)i) / ((double)theNrRandomSubgroups);
			Log.logCommandLine("" + anXs[i] + "\t" + aCopy[i]);
		}

		for (int i=0; i<theNrRandomSubgroups; i++)
		{
			xxBar += (anXs[i] - xBar) * (anXs[i] - xBar);
			xyBar += (anXs[i] - xBar) * (aCopy[i] - yBar);
		}
		double beta1 = xyBar / xxBar;
		double beta0 = yBar - beta1 * xBar;
		// this gives us the regression line y = beta1 * x + beta0
		Log.logCommandLine("Fitted regression line: y = " + beta1 + " * x + " + beta0);
		double aScore = aTopKQuality - beta1 - beta0; // the regression test score now equals the average quality of the top-k subgroups, minus the regression value at x=1.
		Log.logCommandLine("Regression test score: " + aScore);
		return aScore;
	}

	public double[] performRegressionTest(double[] theQualities, SubgroupSet theSubgroupSet)
	{
		double aOne = performRegressionTest(theQualities, 1, theSubgroupSet);
		double aTen = Math.PI;
		if (theSubgroupSet.size()>=10)
			aTen = performRegressionTest(theQualities, 10, theSubgroupSet);
		double[] aResult = {aOne, aTen};
		return aResult;
	}

	public double computeEmpiricalPValue(double[] theQualities, SubgroupSet theSubgroupSet)
	{
		//hardcoded
		int aK = 1;

		// extract average quality of top-k subgroups
		Iterator<Subgroup> anIterator = theSubgroupSet.iterator();
		double aTopKQuality = 0.0;
		for (int i=0; i<aK; i++)
		{
			Subgroup aSubgroup = anIterator.next();
			aTopKQuality += aSubgroup.getMeasureValue();
		}
		aTopKQuality /= aK;

		int aCount = 0;
		for (double aQuality : theQualities)
			if (aQuality > aTopKQuality)
				aCount++;

		Arrays.sort(theQualities);
		Log.logCommandLine("Empirical p-value: " + aCount/(double)theQualities.length);
//		Log.logCommandLine("score at alpha = 1%: " + theQualities[theQualities.length-theQualities.length/100]);
//		Log.logCommandLine("score at alpha = 5%: " + theQualities[theQualities.length-theQualities.length/20]);
//		Log.logCommandLine("score at alpha = 10%: " + theQualities[theQualities.length-theQualities.length/10]);
		return aCount/(double)theQualities.length;
	}

	private ConditionList getRandomConditionList(int theDepth, Random theRandom)
	{
		ConditionList aCL = new ConditionList();

		int aDepth = 1+theRandom.nextInt(theDepth); //random nr between 1 and theDepth (incl)
		int aNrColumns = itsTable.getNrColumns();

		for (int j = 0; j < aDepth; j++) // j conditions
		{
			Column aColumn;
			do
				aColumn = itsTable.getColumn(theRandom.nextInt(aNrColumns));
			while (itsTargetConcept.isTargetAttribute(aColumn));

			Operator anOperator;
			Condition aCondition;
			switch(aColumn.getType())
			{
				case BINARY :
				{
					anOperator = Operator.EQUALS;
					aCondition = new Condition(aColumn, anOperator);
					aCondition.setValue(theRandom.nextBoolean() ? "1" : "0");
					break;
				}
				case NOMINAL :
				{
					anOperator = Operator.EQUALS;
					aCondition = new Condition(aColumn, anOperator);
					TreeSet<String> aDomain = aColumn.getDomain();
					int aNrDistinct = aDomain.size();
					int aRandomIndex = (int) (theRandom.nextDouble()* (double) aNrDistinct);
					Iterator<String> anIterator = aDomain.iterator();
					String aValue = anIterator.next();
					for (int i=0; i<aRandomIndex; i++)
						aValue = anIterator.next();
					aCondition.setValue(aValue);
					break;
				}
				case NUMERIC :
				default :
				{
					anOperator = theRandom.nextBoolean() ?
						Operator.LESS_THAN_OR_EQUAL : Operator.GREATER_THAN_OR_EQUAL;
					aCondition = new Condition(aColumn, anOperator);
					float aMin = aColumn.getMin();
					float aMax = aColumn.getMax();
					aCondition.setValue(
						Float.toString(aMin + (aMax - aMin) / 4 + (aMax - aMin) * theRandom.nextFloat() / 2));
					break;
				}
			}
			aCL.addCondition(aCondition);
		}
		return aCL;
	}
}
