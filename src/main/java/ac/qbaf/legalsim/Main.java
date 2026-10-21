package ac.qbaf.legalsim;

import ac.qbaf.legalsim.simulation.TrialSimulation;

/**
 * נקודת כניסה לשלד — לא מריצה סימולציה עדיין.
 */
public final class Main {
    public static void main(String[] args) {
        TrialSimulation trial = new TrialSimulation();
        trial.run();
    }

    private Main() {
    }
}
