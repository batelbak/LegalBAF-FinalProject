package ac.qbaf.legalsim.optimization;

import ac.qbaf.legalsim.qbaf.QbafGraph;

import java.util.List;
import java.util.Map;

/**
 * כיול θ לכל קטגוריה מול פסקי דין אמיתיים.
 * הערה: DF-QuAD אינה ליניארית ב-τ, ולכן תוכנית לינארית כבר לא מתאימה ישירות — ראו docs/QBAF-MODEL.md.
 */
public class WeightCalibrator {
    public Map<String, Object> fit(List<QbafGraph> graphs, List<String> outcomes) {
        throw new UnsupportedOperationException("skeleton");
    }
}
