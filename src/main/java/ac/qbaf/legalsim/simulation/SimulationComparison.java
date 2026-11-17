package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import ac.qbaf.legalsim.qbaf.QbafSemantics;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import ac.qbaf.legalsim.simulation.TrialSimulation.RoundState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * השוואה בין הגרף מהסימולציה לגרף מהטענות האמיתיות (סעיף 5.6 בסיכום שלב א').
 * מחושב כאן מה שאפשר בלי קריאה נוספת ל-LLM: הכרעה ו-σ(C), מספר טיעונים, מבנה הגרף (עומק, התקפות מול
 * תמיכות), התפלגות ציוני הבסיס לפי קטגוריה, ובאיזה סבב ההכרעה התייצבה.
 * כיסוי הטענות האמיתיות וזיהוי טיעונים חדשים דורשים התאמה סמנטית בין טקסטים ועוד לא ממומשים.
 */
public final class SimulationComparison {

    /**
     * צד אחד של ההשוואה. margin = σ(C) − 0.5 (חיובי: לטובת הפרה).
     * attacks / supports: מספר הקשתות מכל סוג. maxDepth: המרחק הגדול ביותר מ-C (לפי המסלול הקצר של כל טיעון).
     * multiTarget: כמה טיעונים עונים ליותר מקודקוד אחד.
     */
    public record Side(String predicted, double claimStrength, double margin, Map<String, Integer> arguments,
                       int attacks, int supports, int maxDepth, int multiTarget,
                       Map<String, Map<String, Double>> baseScoreByCategory) {
    }

    /**
     * recorded == null כשאין הערכה שמורה לטענות האמיתיות.
     * settledFromRound: הסבב שממנו ההכרעה בסוף כל סבב כבר לא השתנתה. null אם הסימולציה הסתיימה בתיקו.
     */
    public record Comparison(String actual, Side simulated, Side recorded, boolean simulatedMatchesActual,
                             Boolean simulatedMatchesRecorded, List<Integer> leaderChangedInRounds,
                             Integer settledFromRound) {
    }

    public static Comparison compare(LegalCase legalCase, QbafGraph simulatedGraph, Ruling ruling,
                                     List<RoundState> timeline, QbafGraph recordedGraph) {
        Side sim = side(simulatedGraph, ruling);
        Side rec = null;
        if (recordedGraph != null && !recordedGraph.partyArguments().isEmpty()) {
            rec = side(recordedGraph, new QbafSemantics().rule(recordedGraph));
        }

        List<Integer> changes = new ArrayList<>();
        String previous = null;
        for (RoundState state : timeline) {
            if (previous != null && !previous.equals(state.leading())) {
                changes.add(state.round());
            }
            previous = state.leading();
        }

        String predicted = ruling.leading();
        Integer settled = null;
        if (!QbafSemantics.UNDECIDED.equals(predicted)) {
            for (int i = timeline.size() - 1; i >= 0 && predicted.equals(timeline.get(i).leading()); i--) {
                settled = timeline.get(i).round();
            }
        }

        return new Comparison(legalCase.outcome(), sim, rec, predicted.equals(legalCase.outcome()),
                rec == null ? null : predicted.equals(rec.predicted()), changes, settled);
    }

    static Side side(QbafGraph graph, Ruling ruling) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Map<String, Double>> byCategory = new LinkedHashMap<>();
        int attacks = 0;
        int supports = 0;
        int maxDepth = 0;
        int multiTarget = 0;
        for (QbafGraph.Argument a : graph.partyArguments()) {
            counts.merge(a.side(), 1, Integer::sum);
            if (a.category() != null && a.baseScore() != null) {
                byCategory.computeIfAbsent(a.side(), k -> new TreeMap<>())
                        .merge(a.category().id(), a.baseScore(), Double::sum);
            }
            for (QbafGraph.Edge edge : graph.edgesFrom(a.id())) {
                if (edge.relation() == Relation.ATTACK) {
                    attacks++;
                } else {
                    supports++;
                }
            }
            if (graph.edgesFrom(a.id()).size() > 1) {
                multiTarget++;
            }
            maxDepth = Math.max(maxDepth, graph.depth(a.id()));
        }
        return new Side(ruling.leading(), ruling.claimStrength(), ruling.claimStrength() - QbafGraph.NEUTRAL,
                counts, attacks, supports, maxDepth, multiTarget, byCategory);
    }

    private SimulationComparison() {
    }
}
