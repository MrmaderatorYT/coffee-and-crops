package com.ccs.coffee_n_crops.lang.interp;

import com.ccs.coffee_n_crops.farm.Farmhand;

/**
 * Execution events. Every method is called on a farmer worker thread, never the UI thread;
 * UI code must hop to the main thread itself.
 */
public interface ExecListener {
    /** Immutable text describing objects on the worker thread; posted to the UI by the listener. */
    default void onObjectState(int farmerId, String snapshot) { }
    /** Immutable bindings captured on the worker at a paused checkpoint. */
    default void onVariables(int farmerId, String snapshot) { }
    /** A visible farm action reached the point at which it modified the world. */
    default void onFarmAction(Farmhand.Action action) { }
    /** The farmer is about to execute this node (debugger highlight). */
    void onNodeEnter(int farmerId, int nodeId);

    /** The coordinator is about to start a pair of farmhands. */
    default void onTeamStarted() { }

    /** One farmhand's work block has finished or unwound. */
    default void onTeamFarmerFinished(int farmerId) { }

    /** The coordinator has left the team block. */
    default void onTeamFinished() { }

    /** The farmer's program ran to the end. */
    void onFinished(int farmerId);

    /** The program threw an uncaught error at this node; the farmer stopped, the app did not. */
    void onError(int farmerId, int nodeId, String message);
}
