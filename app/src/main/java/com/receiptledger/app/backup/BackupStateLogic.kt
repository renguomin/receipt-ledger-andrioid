package com.receiptledger.app.backup

/** Which system picker a state is waiting on. */
enum class BackupPicker { DESTINATION, SOURCE }

/**
 * Pure reading of [BackupState], shared by the controller and the screen so the two cannot
 * disagree about whether a backup is in flight.
 */
object BackupStateLogic {
    /** Mid-flow, so the buttons stay disabled and nothing new is allowed to start. */
    fun isBusy(state: BackupState): Boolean = when (state) {
        is BackupState.Running,
        is BackupState.ChooseDestination,
        BackupState.ChooseSource,
        BackupState.AwaitingPick,
        -> true
        is BackupState.Idle,
        is BackupState.Ready,
        is BackupState.Restored,
        is BackupState.Failed,
        -> false
    }

    /**
     * The picker this state is asking for, or null when it is asking for none. Once the screen has
     * opened one the state moves to [BackupState.AwaitingPick] and this returns null, which is what
     * stops a re-run of the opening effect from stacking a second picker.
     */
    fun picker(state: BackupState): BackupPicker? = when (state) {
        is BackupState.ChooseDestination -> BackupPicker.DESTINATION
        BackupState.ChooseSource -> BackupPicker.SOURCE
        else -> null
    }
}
