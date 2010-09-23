/*
 * TODO there is only one expression_cuis.txt file, and only one map is needed,
 * make this class to be an enum.
 */
package nl.liacs.subdisc.cui;

import java.io.*;
import java.util.*;

import nl.liacs.subdisc.*;

public class Cui2LineNrMap
{
	// there are 23218 expression cuis
	private static final int DEFAULT_SIZE = 23218;
	private final Map<String, Integer> itsCui2LineNrMap;

	public Cui2LineNrMap(File theFile)
	{
		if (theFile == null || !theFile.exists())
		{
			itsCui2LineNrMap = null;
			ErrorLog.log(theFile, new FileNotFoundException());
			return;
		}
		else
		{
			itsCui2LineNrMap = new HashMap<String, Integer>(DEFAULT_SIZE);
			parseFile(theFile);
		}
	}

	private void parseFile(File theFile)
	{
		BufferedReader aReader = null;

		try
		{
			aReader = new BufferedReader(new FileReader(theFile));
			String aLine;
			int aLineNr = 0;

			while ((aLine = aReader.readLine()) != null)
			{
				// TODO compare with speed of Scanner();
//				anIndexMap.put(aLine.split(",")[0], ++aLineNr);
				itsCui2LineNrMap.put(aLine, ++aLineNr);
			}
		}
		catch (FileNotFoundException e)
		{
			ErrorLog.log(theFile, e);
			return;
		}
		catch (IOException e)
		{
			ErrorLog.log(theFile, e);
			return;
		}
		finally
		{
			try
			{
				if (aReader != null)
					aReader.close();
			}
			catch (IOException e)
			{
				ErrorLog.log(theFile, e);
			}
		}
	}

	/**
	 * Returns the <code>Map<String, Integer></code> for this Cui2LineNrMap.
	 * 
	 * @return the <code>Map<String, Integer></code> for this Cui2LineNrMap, or
	 * <code>null</code> if there is none.
	 */
	public Map<String, Integer> getMap()
	{
		if (itsCui2LineNrMap == null)
			return null;
		else
			return Collections.unmodifiableMap(itsCui2LineNrMap);
	}
}