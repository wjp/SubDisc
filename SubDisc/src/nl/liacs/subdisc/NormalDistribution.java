package nl.liacs.subdisc;

import java.util.Random;

public class NormalDistribution
{
	private static double itsMu;
	private static double itsSigma;
	private static Random itsRandom;
	
	// constructor for standard normal distribution
	public NormalDistribution()
	{
		itsMu = 0.0;
		itsSigma = 1.0;
		itsRandom = new Random(System.currentTimeMillis());
	}
	
	// constructor for normal distribution with general mean and variance
	public NormalDistribution(double theMean, double theVariance)
	{
		itsMu = theMean;
		itsSigma = Math.sqrt(theVariance);
		itsRandom = new Random(System.currentTimeMillis());
	}
	
	public NormalDistribution(double[] theSample, int theLength)
	{
		double aTotalQuality = 0.0;
		for (int i=0; i<theLength; i++)
			aTotalQuality += theSample[i];
		itsMu = aTotalQuality / theLength;

		double aSquareDiff = 0.0;
		for (int j=0; j<theLength; j++)
			aSquareDiff += Math.pow(theSample[j]-itsMu,2);
		itsSigma = Math.sqrt(aSquareDiff/theLength);

		itsRandom = new Random(System.currentTimeMillis());
	}
	
	// deliver next random double from current distribution
	public double getNextDouble()
	{
		return itsSigma * itsRandom.nextGaussian() + itsMu;
	}
	
	// calculate probability density function in the point x
	public double calcPDF(double theX)
	{
		return Math.pow( 
					Math.E, - Math.pow(theX-itsMu,2) / (2*itsSigma*itsSigma) 
				) / ( 
					itsSigma * Math.sqrt( 2*Math.PI ) 
				);
	}
	
	// calculate cumulative distribution function in the point x, based on the error function
	public double calcCDF(double theX)
	{
		return 0.5 * ( 1 + calcErf( 
									(theX - itsMu) / (itsSigma * Math.sqrt(2))
								));
	}
	
	/* calculate error function using Horner's method
	   fractional error in math formula less than 1.2 * 10 ^ -7.
       although subject to catastrophic cancellation when z in very close to 0 */
	public double calcErf(double theZ)
	{
		double aT = 1.0 / (1.0 + 0.5 * Math.abs(theZ));

		double aResult = 1 - aT * Math.exp( -theZ*theZ   -   1.26551223 +
										aT * ( 1.00002368 +
											aT * ( 0.37409196 +
												aT * ( 0.09678418 +
													aT * (-0.18628806 +
														aT * ( 0.27886807 +
															aT * (-1.13520398 +
																aT * ( 1.48851587 +
																	aT * (-0.82215223 +
																		aT * ( 0.17087277))))))))));
		if (theZ >= 0)
			return  aResult;
		else
			return -aResult;
	}
	
	public double getMu() { return itsMu; }
	public double getSigma() { return itsSigma; }
	public double getVariance() { return itsSigma * itsSigma; }
}