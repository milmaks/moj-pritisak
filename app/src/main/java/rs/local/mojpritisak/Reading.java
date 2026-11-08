package rs.local.mojpritisak;

final class Reading {
    final long id;
    final long timestamp;
    final int sys;
    final int dia;
    final int pulse;
    final String note;

    Reading(long id, long timestamp, int sys, int dia, int pulse, String note) {
        this.id = id;
        this.timestamp = timestamp;
        this.sys = sys;
        this.dia = dia;
        this.pulse = pulse;
        this.note = note == null ? "" : note;
    }
}
