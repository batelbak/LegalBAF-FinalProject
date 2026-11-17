package ac.qbaf.legalsim;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent.Submission;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.CaseLoader;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.llm.Llm;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import ac.qbaf.legalsim.report.GraphReport;
import ac.qbaf.legalsim.simulation.RecordedHearing;
import ac.qbaf.legalsim.simulation.SimulationStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * מצב א': הטענות האמיתיות מפסק הדין, בלי סימולציה, כגרף QBAF.
 * <ol>
 *   <li>מבנה: כל סוכן קובע לכל טענה של הצד שלו על מה היא עונה — C ו/או טענות קודמות (נשמר ב-structures).</li>
 *   <li>הסוכנים מכניסים את הטענות לגרף לסירוגין, עם קשתות התקפה ותמיכה לפי המבנה.</li>
 *   <li>השופט קובע ציון בסיס לכל טענה ומסמן כפילויות (נשמר ב-assessments). כפילויות ממוזגות,
 *   וההכרעה מחושבת בסמנטיקה שנבחרה (ברירת מחדל DF-QuAD).</li>
 * </ol>
 * הכול נשמר בתיקיית התוצאות של המודל (Llm.resultsDir) כדי לא להריץ שוב.
 * mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.RealGraphRun -Dexec.args=001-164453
 */
public final class RealGraphRun {

    public static void main(String[] args) throws IOException {
        List<String> ids = java.util.Arrays.stream(args).filter(a -> !a.isBlank()).toList();
        if (ids.isEmpty()) {
            ids = CaseLoader.SAMPLE_IDS;
        }
        CaseLoader loader = new CaseLoader();
        ProsecutorAgent prosecutor = new ProsecutorAgent();
        DefenseAgent defense = new DefenseAgent();
        JudgeAgent judge = new JudgeAgent();
        Path results = Llm.resultsDir(Path.of("data"));
        SimulationStore store = new SimulationStore(results);
        System.out.println("Model: " + Llm.model() + "  results: " + results.toAbsolutePath());

        for (String id : ids) {
            LegalCase legalCase = loader.load(id);

            Map<String, List<String>> structure = store.structure(id);
            if (structure == null) {
                System.out.println(id + "  linking the submissions into a graph...");
                List<Submission> order = RecordedHearing.order(legalCase);
                structure = new LinkedHashMap<>();
                structure.putAll(prosecutor.linkSubmissions(legalCase.facts(), order));
                structure.putAll(defense.linkSubmissions(legalCase.facts(), order));
                structure = RecordedHearing.acyclic(order, structure);
                store.saveStructure(id, structure);
            }
            QbafGraph graph = RecordedHearing.graph(legalCase, structure);

            List<ScoredArgument> scored = store.recordedAssessment(id);
            if (scored == null) {
                System.out.println(id + "  the judge is scoring " + graph.partyArguments().size() + " arguments...");
                scored = judge.assess(legalCase.facts(), JudgeAgent.argumentsIn(graph), graph);
                store.saveAssessment(id, scored);
            }
            // מחילים את ההערכה: כפילויות ממוזגות, ולכל שאר הטענות נקבע ציון בסיס
            RecordedHearing.Built built = RecordedHearing.build(legalCase, scored, structure);
            QbafGraph weighed = built.graph();
            Ruling ruling = judge.decide(weighed);

            Path html = GraphReport.write(results.resolve("graphs"),
                    GraphReport.build(legalCase, scored, weighed, ruling));
            long between = weighed.edges().stream().filter(e -> !QbafGraph.CLAIM.equals(e.to())).count();
            System.out.printf("%s  predicted=%s  actual=%s  sigma(C)=%.3f  (%d arguments, %d edges between arguments, %d merged as duplicates)%n  %s%n",
                    id, ruling.leading(), legalCase.outcome(), ruling.claimStrength(),
                    weighed.partyArguments().size(), between, built.pruned().size(), html.toAbsolutePath());
            Map<String, Map<Integer, Integer>> layers = new java.util.TreeMap<>();
            weighed.partyArguments().forEach(a -> layers.computeIfAbsent(a.side(), s -> new java.util.TreeMap<>())
                    .merge(weighed.depth(a.id()), 1, Integer::sum));
            System.out.println("  arguments per layer (1 = answers C directly): " + layers);
        }
    }

    private RealGraphRun() {
    }
}
