package com.jaguarm.nauvislib.transfer;

/**
 * A battery on the grid: an endpoint that both takes and gives, and does either only when the
 * generators cannot.
 *
 * <p>An electric network tells its endpoints apart by what they refuse - a generator refuses to be
 * filled, a machine refuses to be drained - and an accumulator refuses neither, so without a word
 * from it the network would fill it as a machine and drain it as a generator, and two of them would
 * spend the day passing the same joule back and forth. A handler that carries this marker is
 * handled by Factorio's own rule instead: <b>it takes only what the generators leave over, and it
 * gives only what the generators cannot cover</b>. It never charges from another buffer and never
 * discharges into one.
 *
 * <p>A marker and nothing more, in the library, so a battery in any mod can say so to the power
 * mod without either naming the other - the same seam as {@link FluidBuffer} for a tank. The rate
 * at which it charges and discharges is the handler's own business: the network asks once a tick,
 * and what one call will take or give is the tick's limit.
 */
public interface EnergyBuffer {}
