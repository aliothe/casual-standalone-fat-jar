package se.laz.casual.standalone.outbound

import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

class StaggeredOptionsTest extends Specification
{
   def 'nonsensical staggerfactor'()
   {
      given:
      def staggerFactor = -1
      def initial = Duration.of(50, ChronoUnit.SECONDS)
      def subsequent = Duration.of(50, ChronoUnit.SECONDS)
      when:
      StaggeredOptions.of(initial, subsequent, staggerFactor)
      then:
      thrown(IllegalArgumentException)
      when:
      staggerFactor = 0
      StaggeredOptions.of(initial, subsequent, staggerFactor)
      then:
      thrown(IllegalArgumentException)
   }

   def 'factor 1'()
   {
      given:
      def unit = ChronoUnit.SECONDS
      def staggerFactor = 1
      def initialAmount = 50
      def initial = Duration.of(initialAmount, unit)
      def subsequentAmount = 75
      def subsequent = Duration.of(subsequentAmount, unit)
      def staggeredOptions = StaggeredOptions.of(initial, subsequent, staggerFactor)
      def current = 0
      when:
      current = staggeredOptions.getNext()
      then:
      current.get(unit) == initialAmount
      when:
      current = staggeredOptions.getNext()
      then:
      current.get(unit) == subsequentAmount
   }

   def 'factor 2'()
   {
      given:
      def unit = ChronoUnit.SECONDS
      def staggerFactor = 2
      def initialAmount = 50
      def initial = Duration.of(initialAmount, unit)
      def subsequentAmount = 75
      def subsequent = Duration.of(subsequentAmount, unit)
      def staggeredOptions = StaggeredOptions.of(initial, subsequent, staggerFactor)
      def current = 0
      when:
      current = staggeredOptions.getNext()
      then:
      current.get(unit) == initialAmount
      when:
      current = staggeredOptions.getNext()
      then:
      current.get(unit) == subsequentAmount * staggerFactor
   }

}
