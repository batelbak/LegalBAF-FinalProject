package ac.qbaf.legalsim.qbaf;

import java.util.Map;

/**
 * סמנטיקה הדרגתית (gradual semantics): פונקציית עוצמה σ: X → [0,1] (IJAR 2019, הגדרה 2).
 * העוצמה של כל טיעון נקבעת מציון הבסיס שלו ומהעוצמות של התוקפים והתומכים שלו.
 * המימושים כאן הם שלוש הסמנטיקות שהמאמר מגדיר ל-QBAF כללי על [0,1] (טבלה 1): QuAD, DF-QuAD ו-Euler.
 */
public interface GradualSemantics {
    /** מזהה קצר להגדרות: df-quad / quad / euler. */
    String id();

    /** שם לתצוגה ולתיעוד. */
    String name();

    /** σ לכל קודקוד בגרף, כולל הטענה המרכזית. לכל טיעון בגרף חייב להיות ציון בסיס. */
    Map<String, Double> strengths(QbafGraph graph);
}
