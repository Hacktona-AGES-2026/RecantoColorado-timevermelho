package com.hackatona.timevermelho.agente.provedores;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio que só anda quando o teste manda. */
public class RelogioFalso extends Clock {

    private Instant agora = Instant.parse("2026-10-24T12:00:00Z");

    public void avancar(Duration d) {
        agora = agora.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
