/**
 * File MaxSumADVPFunctionSolver.java
 *
 * This file is part of the jSAM project.
 *
 * Copyright 2016 TNO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.coenvl.sam.solvers;

import java.util.UUID;

import nl.coenvl.sam.MailMan;
import nl.coenvl.sam.agents.ConstraintAgent;
import nl.coenvl.sam.exceptions.InvalidValueException;
import nl.coenvl.sam.messages.HashMessage;
import nl.coenvl.sam.messages.Message;
import nl.coenvl.sam.variables.AssignmentMap;
import nl.coenvl.sam.variables.CostMap;
import nl.coenvl.sam.variables.IntegerVariable;

/**
 * MaxSumADVPFunctionSolver
 *
 * @author leeuwencjv
 * @version 0.1
 * @since 22 jan. 2016
 */
public class MaxSumADVPFunctionSolver extends MaxSumADFunctionSolver<IntegerVariable, Integer> {

    // To make this one generic, we have to forward not values, but valueMaps since there are publishables, and discrete
    // values are not

    private final AssignmentMap<Integer> knownValues;

    public MaxSumADVPFunctionSolver(ConstraintAgent<IntegerVariable, Integer> agent) {
        super(agent);
        this.knownValues = new AssignmentMap<>();
    }

    /*
     * (non-Javadoc)
     *
     * @see nl.coenvl.sam.solvers.Solver#push(nl.coenvl.sam.messages.Message)
     */
    @Override
    public synchronized void push(Message m) {
        super.push(m);

        if (m.containsKey("value")) {
            this.knownValues.put(m.getSource(), m.getInteger("value"));
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see nl.coenvl.sam.solvers.Solver#reset()
     */
    @Override
    public void reset() {
        super.reset();
        this.knownValues.clear();
    }

    /*
     * A message sent from a function-node f to a variable-node x in iteration i includes for each possible value d \in
     * Dx the minimal cost of any combination of assignments to the variables involved in f apart from x and the
     * assignment of value d to variable x.
     *
     * @see nl.coenvl.sam.solvers.IterativeSolver#tick()
     */
    @Override
    public synchronized void tick() {
        this.iterCount++;
        if ((this.iterCount % MaxSumADFunctionSolver.REVERSE_AFTER_ITERS) == 0) {
            this.direction = !this.direction;
        }

        // Only works for binary constraints
        assert (super.numNeighbors() == 2);

        for (UUID target : this.parent.getConstrainedVariableIds()) {
            if ((target.hashCode() > this.parent.hashCode()) == this.direction) {
                continue;
            }

            AssignmentMap<Integer> temp = new AssignmentMap<>();

            // For all values of variable
            CostMap<Integer> costMap = new CostMap<>();
            for (Integer value : this.constraintAgent.getVariableWithID(target)) {
                temp.put(target, value);

                double minCost = Double.MAX_VALUE;
                // Now we know there is only one other neighbor, so iterate for him
                for (UUID other : this.parent.getConstrainedVariableIds()) {
                    if (other == target) {
                        continue;
                    }

                    if (minCost < Double.MAX_VALUE) {
                        throw new InvalidValueException(
                                "The min cost could not be lowered already, more than one agent in constraint?");
                    }

                    if (this.knownValues.containsKey(other)) {
                        // For VP: Only consider known values
                        Integer val2 = this.knownValues.get(other);
                        temp.put(other, val2);

                        double cost = this.parent.getLocalCostIf(temp);
                        if (this.receivedCosts.containsKey(other) && this.receivedCosts.get(other).containsKey(val2)) {
                            cost += this.receivedCosts.get(other).get(val2);
                        }

                        // I think this is redundant, because it will always be true
                        if (cost < minCost) {
                            minCost = cost;
                        }
                    } else {
                        for (Integer val2 : this.constraintAgent.getVariableWithID(other)) {
                            temp.put(other, val2);
                            double cost = this.parent.getLocalCostIf(temp);

                            if (this.receivedCosts.containsKey(other)
                                    && this.receivedCosts.get(other).containsKey(val2)) {
                                cost += this.receivedCosts.get(other).get(val2);
                            }

                            if (cost < minCost) {
                                minCost = cost;
                            }
                        }
                    }
                }

                costMap.put(value, minCost);
            }

            Message msg = new HashMessage(this.constraintAgent.getID(), "FUN2VAR");
            msg.put("costMap", costMap);
            MailMan.sendMessage(target, msg);
        }

        // this.receivedCosts.clear();
    }

    /*
     * (non-Javadoc)
     *
     * @see nl.coenvl.sam.solvers.BiPartiteGraphSolver#getCounterPart()
     */
    @Override
    public Class<? extends BiPartiteGraphSolver> getCounterPart() {
        return MaxSumADVPVariableSolver.class;
    }

}
