package se.motility.linkboy;

/**
 * Class representing how well a certain global taste dimension explains a model, e.g. user preference
 */
public class DimensionStat {

    final int dimIndex;
    final double modelEntropy;
    final double baselineEntropy;
    final double explainedEntropy;

    public DimensionStat(int dimIndex, double modelEntropy, double baselineEntropy) {
        this.dimIndex = dimIndex;
        this.modelEntropy = modelEntropy;
        this.baselineEntropy = baselineEntropy;
        this.explainedEntropy = 1d - modelEntropy/baselineEntropy;
    }

}
