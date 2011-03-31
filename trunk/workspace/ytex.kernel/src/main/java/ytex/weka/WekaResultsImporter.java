package ytex.weka;

import java.io.BufferedReader;
import java.io.IOException;

import ytex.kernel.model.ClassifierEvaluation;
import ytex.weka.WekaResultsImporterImpl.ClassifierEvaluationInstanceImporter;

/**
 * parse weka instance output when classifier run with -p option. load results
 * into db.
 */
public interface WekaResultsImporter {

	/**
	 * load results into document class table. the document id must be the first
	 * attribute in the column of output values.
	 * 
	 * @return
	 */
	public abstract void importDocumentResults(String task,
			BufferedReader reader) throws IOException;

	/**
	 * Parse weka output file, pass results to the specified importer to save
	 * results
	 * 
	 * @param resultInstanceImporter
	 * @param task
	 * @param reader
	 * @throws IOException
	 */
	public abstract void importResults(
			WekaResultInstanceImporter resultInstanceImporter, String task,
			BufferedReader reader) throws IOException;

	public void importClassifierEvaluation(String name, String fold,
			String algorithm, String label, String options, String experiment,
			BufferedReader reader) throws IOException;

}