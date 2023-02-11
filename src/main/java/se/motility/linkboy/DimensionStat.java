/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

/**
 * Class representing how well a certain global taste dimension explains a model, e.g. user preference
 *
 * @author M Tegling
 */
public class DimensionStat {

    private final int dimIndex;
    private final double modelEntropy;
    private final double baselineEntropy;
    private final double explainedEntropy;

    public DimensionStat(int dimIndex, double modelEntropy, double baselineEntropy) {
        this.dimIndex = dimIndex;
        this.modelEntropy = modelEntropy;
        this.baselineEntropy = baselineEntropy;
        this.explainedEntropy = 1d - modelEntropy/baselineEntropy;
    }

    public int getDimIndex() {
        return dimIndex;
    }

    public double getModelEntropy() {
        return modelEntropy;
    }

    public double getBaselineEntropy() {
        return baselineEntropy;
    }

    public double getExplainedEntropy() {
        return explainedEntropy;
    }
}
