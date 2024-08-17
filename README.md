Each oracle can only feed one price,
 *     in the beggining only seeder fillers can
 *     change the oracle price and those are submited in the beggining
 *     when the oracle is created, only when the oracle is open
 *     that other feeders can enter.
 *     The approval of a new price must be done when prices change
 *     at least 1% up or down so anyone who implements this oracle
 *     needs to account for a potential 1% discrepancy or when the
 *     last update was made over 1 hour ago.
 *     Note: This discrepancy can be even higher in moments of high volatily
 *            so take into consideration at least 10% discrepancy in very rare cases
 *    To reach consensus in the price a feeder creates a challenger proposal.
 *    And the other valid feeders can either approve or reject, if
 *    they reject a yellow card is given to challengeOwner and all
 *    that approved. If a challenger is accepcted who rejected receives
 *    a yellow card
 *    If a feeder receives more than 5 yellow cards is expelled forever
 *    New feeders can open a request that takes two days to be approved
 *    Only less than half or half new feeders can enter the oracle every two days cicle
 *       to prevent cordinated attacks
 *
 *       Min nuls recommended to open a oracle and submit info
 *       should be at least  NULS under threat / number of feeders + 1
 *
 *       HIGH RECOMMENDATION: If using this contract when in production
 *                            and public, have at least 7 independent
 *                            running feeders to require at least the need of
 *                            7 malicious feeders to alter the price
 *                            or briberies to the ones already running
 *                            absolute min should be 5k NULS
