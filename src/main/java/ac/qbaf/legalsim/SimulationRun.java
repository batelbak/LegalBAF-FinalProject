package ac.qbaf.legalsim;

import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.data.CaseLoader;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.llm.Llm;
import ac.qbaf.legalsim.simulation.SimulationComparison.Comparison;
import ac.qbaf.legalsim.simulation.SimulationStore;
import ac.qbaf.legalsim.simulation.TrialSimulation;
import ac.qbaf.legalsim.simulation.TrialSimulation.Result;
import ac.qbaf.legalsim.simulation.TrialSimulation.RoundState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * הסימולציה האדברסרית מהשורה: הסוכנים מנסחים טיעונים מתוך העובדות, השופט שוקל אחרי כל סבב.
 * התוצאה נשמרת ב-&lt;results&gt;/simulations, כש-results היא תיקיית המודל (Llm.resultsDir). תיק שכבר יש לו סימולציה שמורה לא רץ שוב, אלא עם --fresh.
 * mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.SimulationRun -Dexec.args="001-164453 --rounds 3"
 * בלי מזהים: כל תיקי המדגם. ברירת המחדל היא מודל מקומי דרך Ollama (ראו Llm).
 */
public final class SimulationRun {
    public static void main(String[] args) throws IOException {
        List<String> ids = new ArrayList<>();
        int rounds = TrialSimulation.DEFAULT_ROUNDS;
        boolean fresh = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--fresh" -> fresh = true;
                case "--rounds" -> rounds = Integer.parseInt(args[++i]);
                default -> {
                    if (!args[i].isBlank()) {
                        ids.add(args[i]);
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            ids = CaseLoader.SAMPLE_IDS;
        }

        CaseLoader loader = new CaseLoader();
        SimulationStore store = new SimulationStore(Llm.resultsDir(Path.of("data")));
        System.out.println("Model: " + Llm.model() + "  results: " + Llm.resultsDir(Path.of("data")).toAbsolutePath());
        TrialSimulation simulation = new TrialSimulation();

        for (String id : ids) {
            Optional<Result> saved = fresh ? Optional.empty() : store.load(id);
            Result result;
            if (saved.isPresent() && saved.get().rounds() == rounds) {
                result = saved.get();
                System.out.println(id + "  (saved simulation, use --fresh to run again)");
            } else {
                LegalCase legalCase = loader.load(id);
                System.out.println(id + "  simulating " + rounds + " rounds...");
                result = simulation.run(legalCase, rounds, PartyAgent.DEFAULT_MAX_ARGUMENTS,
                        store.recordedGraph(legalCase));
                Path html = store.save(result);
                System.out.println("  " + html.toAbsolutePath());
            }
            print(result);
        }
    }

    private static void print(Result r) {
        System.out.printf("  predicted=%s  actual=%s  sigma(C)=%.3f%n", r.predicted(), r.actual(), r.claimStrength());
        for (RoundState s : r.timeline()) {
            System.out.printf("  after round %d: %d arguments, leading=%s, sigma(C)=%.3f%n",
                    s.round(), s.arguments(), s.leading(), s.claimStrength());
        }
        Comparison c = r.comparison();
        if (c.recorded() != null) {
            System.out.printf("  real submissions: predicted=%s  margin %.2f  (simulation margin %.2f)%n",
                    c.recorded().predicted(), c.recorded().margin(), c.simulated().margin());
        }
        System.out.printf("  leader changed in rounds %s, settled from round %s%n",
                c.leaderChangedInRounds(), c.settledFromRound());
    }

    private SimulationRun() {
    }
}
