from __future__ import annotations

import asyncio
import logging
from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from app.temporal.models import (AbandonRoomSignal,GameEndedSignal,GameStartedSignal,PhaseAdvancedSignal,RoomState,)
    from app.temporal.activities.game_activities import (advance_phase_activity,cleanup_room_activity,start_game_activity,start_phase_timer_activity,)

logger = logging.getLogger(__name__)

_RETRY = RetryPolicy(initial_interval=timedelta(seconds=1),backoff_coefficient=2.0,maximum_interval=timedelta(seconds=30),maximum_attempts=3,)

_ACT_OPTS = dict(start_to_close_timeout=timedelta(seconds=30),retry_policy=_RETRY,)


@workflow.defn(name="GameLifecycleWorkflow")
class GameLifecycleWorkflow:
    def __init__(self) -> None:
        self._state: RoomState | None = None
        self._game_started_signal: GameStartedSignal | None = None
        self._pending_phase: str | None = None         
        self._game_over_winner: str | None = None       
        self._abandon: AbandonRoomSignal | None = None 


    @workflow.signal(name="game_started")
    async def on_game_started(self, signal: GameStartedSignal) -> None:
        workflow.logger.info("Signal game_started room_id=%s first_phase=%s",self._room_id(), signal.first_phase,)
        self._game_started_signal = signal

    @workflow.signal(name="phase_advanced")
    async def on_phase_advanced(self, signal: PhaseAdvancedSignal) -> None:
        workflow.logger.info("Signal phase_advanced room_id=%s new_phase=%s",self._room_id(), signal.new_phase,)
        self._pending_phase = signal.new_phase

    @workflow.signal(name="game_ended")
    async def on_game_ended(self, signal: GameEndedSignal) -> None:
        workflow.logger.info("Signal game_ended room_id=%s winner=%s",self._room_id(), signal.winner,)
        self._game_over_winner = signal.winner

    @workflow.signal(name="abandon_room")
    async def on_abandon_room(self, signal: AbandonRoomSignal) -> None:
        workflow.logger.warning("Signal abandon_room room_id=%s reason=%s",self._room_id(), signal.reason,)
        self._abandon = signal


    @workflow.query(name="get_room_state")
    def get_room_state(self) -> dict:
        if self._state is None:
            return {}
        return {
            "room_id":       self._state.room_id,
            "room_code":     self._state.room_code,
            "host_username": self._state.host_username,
            "phase":         self._state.phase,
            "winner":        self._state.winner,
            "is_active":     self._state.is_active,
            "is_game_over":  self._state.is_game_over,
            "phase_history": self._state.phase_history,
        }

    @workflow.run
    async def run(self, room_id: str, room_code: str, host_username: str) -> str:
        self._state = RoomState(room_id=room_id,room_code=room_code,host_username=host_username,)
        workflow.logger.info("GameLifecycleWorkflow started room_id=%s host=%s",room_id, host_username,)

        await workflow.wait_condition(
            lambda: self._game_started_signal is not None or self._abandon is not None,
            timeout=timedelta(hours=2),)

        if self._abandon is not None:
            return await self._handle_abandon(room_id)

        sig = self._game_started_signal
        self._state.is_active = True
        self._state.phase = sig.first_phase
        self._state.phase_history.append(sig.first_phase)

        await workflow.execute_activity(start_phase_timer_activity,args=[room_id, sig.first_phase],**_ACT_OPTS,)

        while True:
            await workflow.wait_condition(
                lambda: (self._pending_phase is not None or self._game_over_winner is not None or self._abandon is not None)
            )
            if self._abandon is not None:
                return await self._handle_abandon(room_id)
            if self._game_over_winner is not None:
                self._state.phase = "GAME_OVER"
                self._state.is_game_over = True
                self._state.winner = self._game_over_winner
                workflow.logger.info("GameLifecycleWorkflow ending room_id=%s winner=%s",room_id, self._game_over_winner,)
                return self._game_over_winner

            new_phase = self._pending_phase
            self._pending_phase = None       

            self._state.phase = new_phase
            self._state.phase_history.append(new_phase)

            workflow.logger.info("GameLifecycleWorkflow phase=%s room_id=%s", new_phase, room_id)

            if new_phase == "GAME_OVER":
                self._state.is_game_over = True
                return self._state.winner or "UNKNOWN"

            await workflow.execute_activity(start_phase_timer_activity,args=[room_id, new_phase],**_ACT_OPTS,)


    async def _handle_abandon(self, room_id: str) -> str:
        reason = self._abandon.reason if self._abandon else "unknown"
        workflow.logger.warning("GameLifecycleWorkflow abandoned room_id=%s reason=%s", room_id, reason)
        if self._state:
            self._state.phase = "ABANDONED"
            self._state.is_active = False

        await workflow.execute_activity(
            cleanup_room_activity,
            args=[room_id, reason],
            start_to_close_timeout=timedelta(seconds=15),
            retry_policy=RetryPolicy(maximum_attempts=2),
        )
        return "ABANDONED"

    def _room_id(self) -> str:
        return self._state.room_id if self._state else "<pending>"
