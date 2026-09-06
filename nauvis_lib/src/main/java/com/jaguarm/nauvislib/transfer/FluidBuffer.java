package com.jaguarm.nauvislib.transfer;

/**
 * A tank that is part of the pipeline rather than at the end of it.
 *
 * <p>A pipe run fills sinks and empties sources, and a machine is one or the other for any fluid.
 * A storage tank is neither: Factorio's is a fluid box in the segment, filling and emptying with
 * the pipes around it, and a run that treated it as a sink would pour everything into it and a
 * run that treated it as a source would pour it all back out. A handler that carries this marker
 * is levelled with the run instead - the run and the tank settle at the same fraction full - and
 * it is otherwise left alone.
 *
 * <p>A marker and nothing more, in the library, so a tank in any mod can say so to the pipe mod
 * without either naming the other.
 */
public interface FluidBuffer {}
