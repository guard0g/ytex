package ytex.kernel.dao;

import java.util.List;
import java.util.Map;

import ytex.kernel.model.ClassifierEvaluation;
import ytex.kernel.model.CrossValidationFold;
import ytex.kernel.model.FeatureEvaluation;
import ytex.kernel.model.FeatureRank;

public interface ClassifierEvaluationDao {

	public abstract void saveClassifierEvaluation(ClassifierEvaluation eval,
			boolean saveInstanceEval);

	public abstract void saveFold(CrossValidationFold fold);

	public abstract void deleteCrossValidationFoldByName(String name,
			String splitName);

	public abstract void saveFeatureEvaluation(
			FeatureEvaluation featureEvaluation);

	public abstract void deleteFeatureEvaluationByNameAndType(
			String corpusName, String featureSetName, String type);

	/**
	 * 
	 * @param eval
	 *            evaluation to save
	 * @param saveInstanceEval
	 *            save instance level evaluations - default false
	 * @param saveIRStats
	 *            save IR statistics - default true
	 * @param excludeTargetClassId
	 *            for semi-supervised learners, don't want to include the
	 *            unlabeled instances in computation of ir statistics. this
	 *            specifies the class id of the unlabeled instances (default 0)
	 */
	public void saveClassifierEvaluation(ClassifierEvaluation eval,
			boolean saveInstanceEval, boolean saveIRStats,
			Integer excludeTargetClassId);

	public abstract CrossValidationFold getCrossValidationFold(
			String corpusName, String splitName, String label, int run, int fold);

	public List<FeatureRank> getTopFeatures(String corpusName,
			String featureSetName, String label, String type, Integer foldId,
			String param1, Integer parentConceptTopThreshold);

	public List<FeatureRank> getThresholdFeatures(String corpusName,
			String featureSetName, String label, String type, Integer foldId,
			String param1, double parentConceptEvaluationThreshold);

	public abstract void deleteFeatureEvaluation(String corpusName,
			String featureSetName, String label, String evaluationType,
			Integer foldId, String param1);

	public abstract Map<String, Double> getFeatureRankEvaluations(
			String corpusName, String featureSetName, String label,
			String evaluationType, Integer foldId, String param1);

	public abstract List<Object[]> getCorpusCuiTuis(String corpusName,
			String conceptGraphName, String conceptSetName);

	public abstract Map<String, Double> getInfoContent(String corpusName,
			String conceptGraphName, String conceptSet);

	public abstract List<FeatureEvaluation> getFeatureEvaluations(
			String corpusName, String featureSetName, String evaluationType,
			String param1);

}