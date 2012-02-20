package nl.liacs.subdisc;

import java.io.*;
import java.util.*;

public class DataLoaderTXT implements FileLoaderInterface
{
	// should be made available to all loaders (through FileLoaderInterface)
	private static final String[] DELIMITERS = { "\\s*\t\\s*", "\\s*,\\s*", "\\s*;\\s*" };

	private Table itsTable = null;
	private String itsDelimiter = DELIMITERS[0];
	private int itsNrLines = 0;

	// default file loader
	public DataLoaderTXT(File theFile)
	{
		String aWarning = null;

		if (theFile == null)
			aWarning = "file can not be null";
		else if (!theFile.exists())
			aWarning = theFile.getAbsolutePath() + ", file does not exist";
		else if (!theFile.canRead())
			aWarning = theFile.getAbsolutePath() + ", file not readable";

		if (aWarning != null)
		{
			// TODO new ErrorDialog(e, ErrorDialog.noSuchFileError);
			message("<init>", aWarning);
			return;
		}

		loadFile(theFile);
	}

	// XML-loader, Table is created based on XML, data is loaded here
	public DataLoaderTXT(File theFile, Table theTable)
	{
		String aWarning = null;

		if (theFile == null)
			aWarning = "file can not be null";
		else if (!theFile.exists())
			aWarning = theFile.getAbsolutePath() + ", file does not exist";
		else if (!theFile.canRead())
			aWarning = theFile.getAbsolutePath() + ", file not readable";

		if (aWarning != null)
		{
			// TODO new ErrorDialog(e, ErrorDialog.noSuchFileError);
			message("<init>", aWarning);
			return;
		}

		itsTable = theTable;
		if (itsTable == null)
			message("<init>", "Table is null, attempting regular file-load.");
		loadFile(theFile);
	}

	private void message(String theMethod, String theMessage)
	{
		Log.logCommandLine(String.format("%s.%s(): %s",
							this.getClass().getSimpleName(),
							theMethod,
							theMessage));
	}

	private void loadFile(File theFile)
	{
		// this will get two BufferedReaders, prevents TOCTOE bugs
		// analyse establishes the number of data lines and delimiter
		if (!analyse(theFile))
			return;

		BufferedReader aReader = null;
		try
		{
			aReader = new BufferedReader(new FileReader(theFile));
			String aHeaderLine = null;
			String aLine;
			int aLineNr = 0;

			// skip header, make sure line is not empty/ null
			while ((aLine = aReader.readLine()) != null)
			{
				++aLineNr;
				if (!aLine.isEmpty())
				{
					aHeaderLine = aLine;
					break;
				}
			}

			// used for XML sanity check later
			AttributeType[] anOriginalTypes = null;
			// loaded from XML
			if (itsTable != null)
			{
				anOriginalTypes = checkXMLTable(aHeaderLine, theFile);
				// something is seriously wrong
				if (anOriginalTypes == null)
					return;
			}
			else
			{
				// read first data line, create Table based on it
				while ((aLine = aReader.readLine()) != null)
				{
					++aLineNr;
					if (!aLine.isEmpty())
					{
						createTable(theFile, aHeaderLine, aLine);
						break;
					}
				}
			}

			BitSet aBinaries = new BitSet(itsNrLines);
			BitSet aFloats = new BitSet(itsNrLines);
			List<Column> aColumns = itsTable.getColumns();
			final int aNrColumns = aColumns.size();
			for (int i = 0, j = aNrColumns; i < j; ++i)
			{
				if (aColumns.get(i).isBinaryType())
					aBinaries.set(i);
				else if (aColumns.get(i).isNumericType())
					aFloats.set(i);
				// no use case yet
				//else if (aColumns.get(i).isOrdinalType())
				//	aFloats.set(i);
			}

			message("loadFile", "loading data");
			// code ignores AttributeType.ORDINAL
			while ((aLine = aReader.readLine()) != null)
			{
				++aLineNr;
				if (aLine.isEmpty())
					continue;

				/*
				 * Scanner is faster for long lines, but it is
				 * harder to identify faulty lines.
				 * Using .split() this would be trivial.
				 */
				Scanner aScanner = new Scanner(aLine).useDelimiter(itsDelimiter);

				//read fields
				int aColumn = -1;
				while (aScanner.hasNext() && aColumn < aNrColumns)
				{
					++aColumn;
					String s = aScanner.next();
					removeQuotes(s);

					// is it binary
					if (aBinaries.get(aColumn))
					{
						if (isEmptyString(s))
						{
							aColumns.get(aColumn).add(AttributeType.BINARY.DEFAULT_MISSING_VALUE);
							continue;
						}
						else if (AttributeType.isValidBinaryValue(s))
						{
							aColumns.get(aColumn).add("1".equals(s));
							continue;
						}

						// no longer a binary type
						aBinaries.set(aColumn, false);
						// set to numeric, use fall through
						aFloats.set(aColumn, true);
					}

					// is it numeric
					if (aFloats.get(aColumn))
					{
						try
						{
							if (isEmptyString(s))
								aColumns.get(aColumn).add(AttributeType.NUMERIC.DEFAULT_MISSING_VALUE);
							else
								aColumns.get(aColumn).add(Float.parseFloat(s));
							continue;
						}
						catch (NumberFormatException e)
						{
							aFloats.set(aColumn, false);
						}
					}

					// is it ordinal
					// NO USE CASE YET

					// it is nominal
					if (isEmptyString(s))
						aColumns.get(aColumn).add(AttributeType.NOMINAL.DEFAULT_MISSING_VALUE);
					else
						aColumns.get(aColumn).add(s);
				}
				if (aColumn != aNrColumns-1)
					message("loadFile", "error on line " + aLineNr);
				if (aLineNr % 10000 == 0)
					message("loadFile", aLineNr + " lines read");
			}

			// one final check about the validity of the XML file
			if (anOriginalTypes != null)
			{
				evaluateXMLLoading(anOriginalTypes, theFile);
			}
		}
		catch (IOException e)
		{
//			new ErrorDialog(e, ErrorDialog.fileReaderError);
			e.printStackTrace();
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
//				new ErrorDialog(e, ErrorDialog.fileReaderError);
				e.printStackTrace();
			}
		}
	}

	// cumbersome, but cleanly handles empty lines before/ after header line
	private boolean analyse(File theFile)
	{
		message("analyse", "analysing " + theFile.getAbsolutePath());
		boolean aSuccess = false;
		BufferedReader aReader = null;

		try
		{
			aReader = new BufferedReader(new FileReader(theFile));
			String aHeaderLine;
			String aLine;
			int aNrDataLines = 0;

			// find first non empty line (header line)
			while ((aHeaderLine = aReader.readLine()) != null)
				if (!aHeaderLine.isEmpty())
					break;

			// find second non empty line (to determine delimiter)
			while ((aLine = aReader.readLine()) != null)
			{
				if (!aLine.isEmpty())
				{
					++aNrDataLines;
					establishDelimiter(aHeaderLine, aLine);
					break;
				}
			}

			// check on number of columns is deferred to loadFile
			while ((aLine = aReader.readLine()) != null)
				if (!aLine.isEmpty())
					++aNrDataLines;

			message("analyse", aNrDataLines + " lines found");
			itsNrLines = aNrDataLines;
			aSuccess = true;
		}
		catch (IOException e)
		{
			message("analyse", "IOException caused by file: " + theFile.getPath());
			e.printStackTrace();
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
				message("analyse", "IOException caused by file: " + theFile.getPath());
				e.printStackTrace();
			}
		}
		return aSuccess;
	}

	private void establishDelimiter(String theFirstLine, String theSecondLine)
	{
		int aNrDelimiters = DELIMITERS.length;
		int[] aCounts = new int[aNrDelimiters];
		int aNrOptions = 0;
		String aMessage;

		for (int i = 0, j = aNrDelimiters; i < j; ++i)
		{
			aCounts[i] = theFirstLine.split(DELIMITERS[i], -1).length;
			if (aCounts[i] > 1)
				++aNrOptions;
		}

		if (aNrOptions == 0)
			aMessage = "unable to determine delimiter, using: ";
		else if (aNrOptions == 1)
		{
			for (int i = 0, j = aNrDelimiters; i < j; ++i)
				if (aCounts[i] > 1)
					itsDelimiter = DELIMITERS[i];
			aMessage = "successfully established delimiter, using: ";
		}
		else // (aNrOptions > 1)
		{
			for (int i = 0, j = aNrDelimiters; i < j; ++i)
			{
				if (aCounts[i] > 1)
				{
					// just pick the first one
					if (aCounts[i] == theSecondLine.split(DELIMITERS[i], -1).length)
					{
						itsDelimiter = DELIMITERS[i];
						aMessage = "unsure about delimiter, using: ";
						break;
					}
				}
			}

			aMessage = "unable to determine delimiter, using: ";
		}
		message("establishDelimiter", aMessage + itsDelimiter);
	}

	// check the XML declared Table ColumnNames against the HeaderLine
	// the returned array contains the ColumnTypes as declared in XML
	private AttributeType[] checkXMLTable(String theHeaderLine, File theFile)
	{
		final String[] aHeaders = theHeaderLine.split(itsDelimiter, -1);
		final int aNrColumns = aHeaders.length;
		boolean returnNull = false;

		// check if number of columns is equal in XML and File
		if (aNrColumns != itsTable.getColumns().size())
		{
			message("checkXMLTable",
					String.format("ERROR%nNumber of Columns declared in XML: %d%nNumber of Columns retrieved from File %s: %d",
							itsTable.getColumns().size(),
							theFile.getName(),
							aNrColumns));
			returnNull = true;
		}

		// check whether ColumnNames are equal in XML and File
		for (int i = 0; i < aNrColumns; ++i)
		{
			if (!aHeaders[i].equals(itsTable.getColumn(i).getName()))
			{
				message("checkXMLTable",
					String.format("ERROR on index %d%nColumn '%s' from XML does not match Column '%s' from File '%s'",
							(i+1),
							itsTable.getColumn(i).getName(),
							aHeaders[i].trim(),
							theFile.getName()));
				returnNull = true;
				break;
			}
		}

		if (returnNull)
			return null;
		else
		{
			final AttributeType[] theOriginalTypes = new AttributeType[aNrColumns];
			for (int i = 0, j = aNrColumns; i < j; ++i)
				theOriginalTypes[i] = itsTable.getColumn(i).getType();
			return theOriginalTypes;
		}
	}

	// create Table Columns using HeaderLine names, base Type on DataLine
	private void createTable(File theFile, String aHeaderLine, String aDataLine)
	{
		message("createTable", "creating Table");
		String[] aHeaders = aHeaderLine.split(itsDelimiter);
		String[] aData = aDataLine.split(itsDelimiter);

		// for-each loop might not work for data changes
		for (String s : aHeaders)
			removeQuotes(s);

		// create Table and Columns
		itsTable = new Table(theFile, itsNrLines, aHeaders.length);
		List<Column> aColumns = itsTable.getColumns();

		for (int i = 0, j = aHeaders.length; i < j; ++i)
		{
			String s = aData[i];
			removeQuotes(s);

			// is it binary (or empty String)
			if (AttributeType.isValidBinaryValue(s) || isEmptyString(s))
			{
				aColumns.add(new Column(aHeaders[i],
							null,
							AttributeType.BINARY,
							i,
							itsNrLines));
				aColumns.get(i).add("1".equals(s));
				continue;
			}

			// is it numeric
			try
			{
				float f = Float.parseFloat(s);
				aColumns.add(new Column(aHeaders[i],
							null,
							AttributeType.NUMERIC,
							i,
							itsNrLines));
				aColumns.get(i).add(f);
				continue;
			}
			catch (NumberFormatException e) {}

			// is it ordinal
			// NO USE CASE YET

			// it is nominal
			aColumns.add(new Column(aHeaders[i],
						null,
						AttributeType.NOMINAL,
						i,
						itsNrLines));
			aColumns.get(i).add(s);
		}
	}

	// NOTE null is never passed as input parameter
	private void removeQuotes(String theString)
	{
		// fail fast
		if ((theString.charAt(0) != '\"') && (theString.charAt(0) != '\''))
			return;

		int aLength = theString.length();
		if (	(((theString.charAt(0) == '\"') && (theString.charAt(aLength-1) == '\"')) ||
			((theString.charAt(0) == '\'') && (theString.charAt(aLength -1) == '\''))) &&
			(aLength > 2))
				theString = theString.substring(1, theString.length()-1);
	}

	// NOTE null is never passed as input parameter
	private boolean isEmptyString(String s)
	{
		return (s.matches("\\s*"));
	}

	private void evaluateXMLLoading(AttributeType[] theOriginalTypes, File theFile)
	{
		for (int i = 0, j = theOriginalTypes.length; i < j; ++i)
			if (itsTable.getColumn(i).getType() != theOriginalTypes[i])
				message("evaluateXMLLoading",
					String.format("WARNING Column '%s'%nXML declared AttributeType: '%s'%nAttributeType after parsing File %s",
							itsTable.getColumn(i).getName(),
							theOriginalTypes[i].toString(),
							itsTable.getColumn(i).getType(),
							theFile.getPath()));
	}

	@Override
	public Table getTable()
	{
		// TODO will still return a table, even if no data is loaded, change
		// MiningWindow could fall back to 'no table' if itsTable.getNrRows == 0
		return itsTable;
	}
}
