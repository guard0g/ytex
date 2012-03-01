package ytex.wsd.nlm;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import ytex.kernel.SimSvcContextHolder;
import ytex.kernel.metric.ConceptSimilarityService;
import ytex.kernel.metric.ConceptSimilarityService.SimilarityMetricEnum;
import ytex.kernel.wsd.WordSenseDisambiguator;

/**
 * disambiguate nlm wsd dataset using specified semantic similarity measure.
 * required parameters:
 * <ul>
 * <li>metric @see SimilarityMetricEnum
 * <li>windowSize number of concepts on either side of the target concept to use
 * for disambiguation
 * </ul>
 * will write tab-delimited file to [metric].txt in working directory. columns:
 * <ul>
 * <li>instanceId
 * <li>word
 * <li>target cui
 * <li>predicted cui
 * <li>scores for each cui
 * </ul>
 * 
 * @author vijay
 * 
 */
public class WSDDisambiguator {
	private static final Log log = LogFactory.getLog(WSDDisambiguator.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		SimilarityMetricEnum metric = SimilarityMetricEnum.valueOf(args[0]);
		int windowSize = Integer.parseInt(args[1]);
		WSDDisambiguator wsd = new WSDDisambiguator();
		wsd.disambiguate(metric, windowSize);
	}

	public static class Sentence {
		long instanceId = -1;
		int index = -1;
		List<Set<String>> concepts = new ArrayList<Set<String>>();

		public Sentence(long instanceId) {
			super();
			this.instanceId = instanceId;
		}

		public int getIndex() {
			return index;
		}

		public long getInstanceId() {
			return instanceId;
		}

		public void setInstanceId(long instanceId) {
			this.instanceId = instanceId;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public List<Set<String>> getConcepts() {
			return concepts;
		}

		public void setConcepts(List<Set<String>> concepts) {
			this.concepts = concepts;
		}
	}

	public static class Word {
		public long getInstanceId() {
			return instanceId;
		}

		public void setInstanceId(int instanceId) {
			this.instanceId = instanceId;
		}

		public String getWord() {
			return word;
		}

		public void setWord(String word) {
			this.word = word;
		}

		public String getCui() {
			return cui;
		}

		public void setCui(String cui) {
			this.cui = cui;
		}

		public int getSpanBegin() {
			return spanBegin;
		}

		public void setSpanBegin(int spanBegin) {
			this.spanBegin = spanBegin;
		}

		public int getSpanEnd() {
			return spanEnd;
		}

		public void setSpanEnd(int spanEnd) {
			this.spanEnd = spanEnd;
		}

		public Word(long instanceId, String word, String cui, int spanBegin,
				int spanEnd) {
			super();
			this.instanceId = instanceId;
			this.word = word;
			this.cui = cui;
			this.spanBegin = spanBegin;
			this.spanEnd = spanEnd;
		}

		long instanceId;
		String word;
		String cui;
		int spanBegin;
		int spanEnd;
	}

	Map<String, Set<String>> wordCuis;
	Map<Long, Set<String>> titleConcepts;
	Map<Long, Word> words;
	JdbcTemplate jdbcTemplate;
	SortedMap<Long, Sentence> sentences;
	WordSenseDisambiguator wordSenseDisambiguator;

	public WSDDisambiguator() {
		DataSource ds = (DataSource) SimSvcContextHolder
				.getApplicationContext().getBean("dataSource");
		this.jdbcTemplate = new JdbcTemplate(ds);
		this.wordSenseDisambiguator = SimSvcContextHolder
				.getApplicationContext().getBean(WordSenseDisambiguator.class);
	}

	public Map<String, Set<String>> loadWordCuis() {
		wordCuis = new HashMap<String, Set<String>>();
		jdbcTemplate.query("select word, cui from nlm_wsd_cui order by word",
				new RowCallbackHandler() {
					String wordCurrent = null;
					Set<String> cuisCurrent = null;

					@Override
					public void processRow(ResultSet rs) throws SQLException {
						String wordNew = rs.getString(1);
						String cui = rs.getString(2);
						if (wordCurrent == null || !wordCurrent.equals(wordNew)) {
							cuisCurrent = new HashSet<String>();
							wordCurrent = wordNew;
							wordCuis.put(wordCurrent, cuisCurrent);
						}
						cuisCurrent.add(cui);
					}
				});
		return wordCuis;
	}

	public Map<Long, Set<String>> loadTitleConcepts() {
		titleConcepts = new HashMap<Long, Set<String>>();
		jdbcTemplate
				.query("select uid, code from document d inner join anno_base b on d.document_id = b.document_id inner join anno_ontology_concept c on c.anno_base_id = b.anno_base_id  where d.analysis_batch = 'wsd-title' order by uid",
						new RowCallbackHandler() {
							long wordCurrent = -1;
							Set<String> cuisCurrent = null;

							@Override
							public void processRow(ResultSet rs)
									throws SQLException {
								long wordNew = rs.getLong(1);
								String cui = rs.getString(2);
								if (wordCurrent != wordNew) {
									cuisCurrent = new HashSet<String>();
									wordCurrent = wordNew;
									titleConcepts.put(wordCurrent, cuisCurrent);
								}
								cuisCurrent.add(cui);
							}
						});
		return titleConcepts;
	}

	public Map<Long, Word> loadWords() {
		words = new HashMap<Long, Word>();
		jdbcTemplate
				.query("select w.instance_id, w.word, coalesce(c.cui, w.choice_code) cui, w.abs_ambiguity_start spanBegin, w.abs_ambiguity_end+1 spanEnd from nlm_wsd w left join nlm_wsd_cui c on w.word = c.word and w.choice_code = c.choice_code",
						new RowCallbackHandler() {
							@Override
							public void processRow(ResultSet rs)
									throws SQLException {
								Word w = new Word(rs.getLong(1), rs
										.getString(2), rs.getString(3), rs
										.getInt(4), rs.getInt(5));
								words.put(w.getInstanceId(), w);
							}
						});
		return words;
	}

	public Map<Long, Sentence> loadSentences() {
		sentences = new TreeMap<Long, Sentence>();
		jdbcTemplate
				.query("select d.uid instance_id, b.span_begin, b.span_end, c.code from document d inner join anno_base b on d.document_id = b.document_id inner join anno_ontology_concept c on c.anno_base_id = b.anno_base_id where d.analysis_batch = 'nlm.wsd' order by d.uid, span_begin, span_end",
						new RowCallbackHandler() {
							int currentSpanBegin = -1;
							int currentSpanEnd = -1;
							Sentence currentSentence = null;
							Set<String> currentConcepts = null;
							int currentConceptIndex = -1;

							@Override
							public void processRow(ResultSet rs)
									throws SQLException {
								long instanceId = rs.getLong(1);
								int spanBegin = rs.getInt(2);
								int spanEnd = rs.getInt(3);
								String cui = rs.getString(4);
								if (currentSentence == null
										|| currentSentence.getInstanceId() != instanceId) {
									// new word
									reset(instanceId);
								}
								if (currentConcepts == null
										|| currentSpanBegin != spanBegin
										|| currentSpanEnd != spanEnd) {
									// new concept
									resetCurrentConcepts(spanBegin, spanEnd);
								}
								// don't touch the concept that's supposed to
								// be disambiguated
								if (currentConceptIndex != currentSentence
										.getIndex()) {
									currentConcepts.add(cui);
								}
								if (rs.isLast())
									checkSentenceTargetIndex();
							}

							private void resetCurrentConcepts(int spanBegin,
									int spanEnd) {
								// increment index
								currentConceptIndex++;
								// allocate new set for concepts
								currentConcepts = new HashSet<String>();
								// add the set to the sentence
								currentSentence.getConcepts().add(
										currentConcepts);
								Word w = words.get(currentSentence
										.getInstanceId());
								if (currentSentence.getIndex() < 0
										&& w.spanBegin < spanBegin) {
									// we didn't have the target concept
									// annotated as a named entity, and we've
									// passed the target concept.
									// insert the target concept into the
									// sentence
									currentSentence
											.setIndex(currentConceptIndex);
									currentConcepts.addAll(wordCuis.get(w
											.getWord()));
									// reset again
									resetCurrentConcepts(spanBegin, spanEnd);
								}

								if (w.getSpanBegin() == spanBegin
										&& w.spanEnd == spanEnd) {
									// this concept is the target for
									// disambiguation
									currentSentence
											.setIndex(currentConceptIndex);
									currentConcepts.addAll(wordCuis.get(w
											.getWord()));
								}
							}

							private void reset(long instanceId) {
								checkSentenceTargetIndex();
								currentSpanBegin = -1;
								currentSpanEnd = -1;
								currentSentence = new Sentence(instanceId);
								currentConcepts = null;
								currentConceptIndex = -1;
								sentences.put(instanceId, currentSentence);
							}

							private void checkSentenceTargetIndex() {
								if (currentSentence != null
										&& currentSentence.getIndex() == -1) {
									Word w = words.get(currentSentence
											.getInstanceId());
									// we didn't have the target concept
									// annotated as a named entity, and we've
									// come to the end of the sentence.
									// insert the target concept into the
									// sentence.
									// this would be a problem if
									currentSentence
											.setIndex(currentConceptIndex + 1);
									currentConcepts = new HashSet<String>();
									currentConcepts.addAll(wordCuis.get(w
											.getWord()));
									currentSentence.getConcepts().add(
											currentConcepts);
								}
							}
						});
		return sentences;
	}

	public void disambiguate(
			ConceptSimilarityService.SimilarityMetricEnum metric, int windowSize)
			throws IOException {
		this.loadWordCuis();
		this.loadWords();
		this.loadSentences();
		this.loadTitleConcepts();
		
		PrintStream ps = null;
		try {
			log.info("disambiguate start: " + (new Date()));
			ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(
					metric.name() + ".txt")));
			for (Map.Entry<Long, Sentence> sentEntry : sentences.entrySet()) {
				long instanceId = sentEntry.getKey();
				Sentence s = sentEntry.getValue();
				Word w = words.get(instanceId);
				Set<String> title = titleConcepts.get(instanceId);
				ps.print(instanceId);
				ps.print("\t");
				ps.print(w.getWord());
				ps.print("\t");
				ps.print(w.getCui());
				ps.print("\t");
				Map<String, Double> scoreMap = new HashMap<String, Double>();
				String cui = this.wordSenseDisambiguator.disambiguate(
						s.getConcepts(), s.getIndex(), title, windowSize,
						metric, scoreMap);
				ps.print(cui);
				ps.print("\t");
				ps.println(scoreMap);
			}
			log.info("disambiguate end: " + (new Date()));
		} finally {
			if (ps != null) {
				try {
					ps.close();
				} catch (Exception e) {
				}
			}
		}
	}
}