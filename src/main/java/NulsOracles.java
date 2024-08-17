import ch.qos.logback.core.util.COWArrayList;
import com.fasterxml.jackson.databind.BeanProperty;
import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.*;
import io.nuls.contract.sdk.event.DebugEvent;
import org.checkerframework.checker.units.qual.A;

import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reentrancyguard.ReentrancyGuard;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @notice Nuls Contract that feeds price info into contracts
 *           that need off chain prices
 *
 * @dev Each oracle can only feed one price,
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
 *
 *       Developed by Pedro G. S. Ferreira @Pedro_Ferreir_a
 * */
public class NulsOracles extends ReentrancyGuard implements Contract{

    /** 100 NULS
     *   @dev Min required to deposit in aiNULS is 100 NULS
     */
    private static final BigInteger ONE_NULS        = BigInteger.valueOf(100000000L); // 1 NULS
    private static final BigInteger FIVEPER_NULS    = BigInteger.valueOf(5000000L);   // 0.05 NULS
    private static final BigInteger TWO             = BigInteger.valueOf(2);          // 2
    private static final BigInteger FIVE            = BigInteger.valueOf(5);          // 5
    private static final BigInteger FEE_NULS        = BigInteger.valueOf(10000000L);  // 0.1 NULS
    private static final BigInteger SLASH_FEE       = BigInteger.valueOf(1000000000L);// 10 NULS
    private static final BigInteger BASIS_POINTS    = BigInteger.valueOf(10000);      // 10.000
    private static final BigInteger BASIS_1PLUS     = BigInteger.valueOf(10100);      // 10.100
    private static final BigInteger BASIS_1MINUS    = BigInteger.valueOf(9900);      // 9.900
    private static final BigInteger ONE_HOUR        = BigInteger.valueOf(60 * 60);    // 1 hour
    private static final BigInteger RAT_OUT_PAYOUT  = BigInteger.valueOf(500000000L); // 5 NULS
    private static final BigInteger INACTIVE_PAYOUT = BigInteger.valueOf(10000000L);  // 0.1 NULS
    private static final BigInteger TWO_DAYS        = BigInteger.valueOf(60 * 60 * 24 * 2); // 2 days
    private static final int TWO_DAYS_LONG          = 60 * 60 * 24 * 2; // 2 days
    private static final int THREE_DAYS_LONG        = 60 * 60 * 24 * 3; // 3 days
    private static final long FIVE_DAYS             = 60 * 60 * 24 * 5; // 5 days

    public Address token; // Project Token
    private BigInteger tokenTotalSupply;
    public Boolean paused;

    public BigInteger minNULSForFeeder;
    public BigInteger minValids; // Min number of submissions required for filling info in oracle
    public BigInteger pricePerRead;
    public BigInteger priceForFeederValid;
    public BigInteger penaltiesLeftOver; //penalties charged for fillers non compliant
    public Address treasury;
    public Map<Address, Boolean> projectAdmin = new HashMap<>();

    public Integer oracleCounter;


    public BigInteger oracle;           //price of asset
    public BigInteger oracleLastUpdated;// When was the last oracle update
    public BigInteger challenger;       // Challenger price to current

    public Address challengerOwner;     // Challenger price to current
    public Integer challengerApprovs;   // Challenger price to current
    public Integer challengerRejects;   // Challenger price to current
    public Integer validFeedinOracle;   // number of approved feeders
    public int pendingNewFeeders;

   //User Balance
    public Map<Address, BigInteger> userBalance             = new HashMap<>(); // Amount deposited to check if can fill oracle needs
    public Map<Address, BigInteger> minValidationsToSubmit  = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable
    public Map<Address, Long> firstSubmissionToOracle       = new HashMap<>(); // Feeders must wait 2 days until they submit info, this stores the submission request
    public Map<Address, Integer> yellowCard                 = new HashMap<>(); // User yellow cards
    public Map<Address, Long> lastUserSubmit                = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable
    public Map<Address, Boolean> oracleSeedFillers          = new HashMap<>(); // check if is seed in oracle
    public Map<Address, Boolean> oracleNormalFillers        = new HashMap<>(); // check if is seed in oracle

    public Map<Integer, Map<Address, Boolean>> currentSubmission = new HashMap<>();; //results of the challenge
    public Boolean onlySeeders; // If true only seeders can submit

    public int challengerCounter;
    public Map<Integer, Boolean> challengerResult        = new HashMap<>(); // Amount deposited to check if can fill oracle needs

    //--------------------------------------------------------------------
    //Initialize Contract
    public NulsOracles(@Required BigInteger pricePerRead_,
                       @Required BigInteger priceForFeederValid_,
                       @Required BigInteger minNULSForFeeder_,
                       @Required BigInteger minValids_,
                       @Required Address token_,
                       @Required Address admin_,
                       @Required Address treasury_,
                       @Required String[] seeders_

    ) {
        // Min nuls to submit info needs to pay the yellow card payments
        require(minNULSForFeeder_.compareTo(RAT_OUT_PAYOUT.multiply(FIVE)) >= 0, "Min Nuls must be higher");
        require(seeders_.length < 3, "MAx 3 seeders");


        projectAdmin.put(admin_, true);
        onlySeeders         = true;
        paused              = false;
        minNULSForFeeder    = minNULSForFeeder_;
        pricePerRead        = pricePerRead_;
        token               = token_;
        minValids           = minValids_;
        priceForFeederValid = priceForFeederValid_;
        treasury            = treasury_;
        penaltiesLeftOver   = BigInteger.ZERO;
        tokenTotalSupply    = new BigInteger(token.callWithReturnValue("totalSupply", "", null, BigInteger.ZERO));

        challengerCounter   = 0;
        oracleCounter       = 0;
        pendingNewFeeders   = 0;

        for(int i = 0; i < seeders_.length; i++)
            oracleSeedFillers.put(new Address(seeders_[i]), true);
    }

    /** VIEW FUNCTIONS */

    /**
     * @notice Get Oracle Project Token
     *
     * @return oracle project Token Contract Address
     */
    @View
    public Address getOracleNulsoken() {
        return token;
    }


    /**
     * @notice Verify if Address is admin
     *
     * @return true if it is admin, false if not
     */
    @View
    public Boolean isAdmin(Address admin) {
        if(projectAdmin.get(admin) == null){
            return false;
        }
        return projectAdmin.get(admin);
    }

    /**
     * @notice Get user balance deposited in lock
     *
     * @return User Balance
     */
    @View
    public BigInteger getUserBalance(Address addr){
        if(userBalance.get(addr) == null)
            return BigInteger.ZERO;
        return userBalance.get(addr);
    }

    /**
     * @notice Get user balance deposited in lock
     *
     * @return User Balance
     */
    @View
    public Boolean newChallenger(){
        if(challenger != null)
            return true;
        return false;
    }


    @View
    public Boolean isPaused(){
        return paused;
    }

    /** MODIFIER FUNCTIONS */

    /***
     * Require that
     *
     * */
    protected void onlyAdmin(){
        require(projectAdmin.get(Msg.sender()) != null && projectAdmin.get(Msg.sender()), "Invalid Admin");
    }

    protected void notPaused(){
        require(!paused, "Contract is paused");
    }


    protected void onlyIfFeederHasDeposit(Address feeder){
        require(userBalance.get(feeder).compareTo(minNULSForFeeder) >= 0, "NulsOracleV1: Min Nuls Required to Submit");
    }

    protected void onlyIfValidYellowCards(Address feeder){
        require(yellowCard.get(feeder) <= 5, "NulsOraclesV1: Expelled from feeders");
    }

    public void openOracleToPublic(){
        onlyAdmin();
        onlySeeders = false;
    }

    @Payable
    public void enterNewFeeder(){

        // New Feeder needs already a deposit
        onlyIfFeederHasDeposit(Msg.sender());

        require(pendingNewFeeders < validFeedinOracle / 2, "Only allow less than half of approved for every cicle");
        require(Msg.value().compareTo(RAT_OUT_PAYOUT.multiply(FIVE)) >= 0, "Pay for payouts");
        firstSubmissionToOracle.put(Msg.sender(), Block.timestamp());
        pendingNewFeeders += 1;

    }

    public void completeProcess(int seedersNumber){

        require(firstSubmissionToOracle.get(Msg.sender()) != null, "Process is null");
        require((firstSubmissionToOracle.get(Msg.sender()) + THREE_DAYS_LONG) < Block.timestamp() , "Two days waiting period");

        //Prevent double submissions
        firstSubmissionToOracle.put(Msg.sender(), null);
        validFeedinOracle += 1;
        oracleNormalFillers.put(Msg.sender(), true);
        pendingNewFeeders -= 1;
    }


    public void iAmActive(){
        // Check to prevent withdraws until 2 daus after price submit and to check that oracle is active
        lastUserSubmit.put(Msg.sender(), Block.timestamp());
    }

    public void alertInactive( Address inactiveUser){

        require(validFeedinOracle > 1, "Last filler is always right");

        Boolean b = oracleNormalFillers.get(inactiveUser);
        require(b != null && b, "Not feeder");

        require(lastUserSubmit.get(inactiveUser) + TWO_DAYS_LONG <= Block.timestamp(), "User is active");

        oracleNormalFillers.put(inactiveUser, false);
        validFeedinOracle =  validFeedinOracle - 1;
        Msg.sender().transfer(INACTIVE_PAYOUT);

    }

    private void approveChallenger(){
        // Check if there is a pending approve or reject
        if(challengerApprovs >= (validFeedinOracle / 2 + 1)){

            oracle            = challenger;
            oracleLastUpdated =  BigInteger.valueOf(Block.timestamp());
            challengerApprovs = 0;
            challengerRejects = 0;
            challenger        = null;
            challengerResult.put(challengerCounter++, true);

        }

    }

    private void rejectChallenger(){
        if(challengerRejects >= (validFeedinOracle / 2 + 1)){

            increaseUserYellowCards(challengerOwner);

            challengerApprovs = 0;
            challengerRejects = 0;
            challenger = null;
            challengerResult.put(challengerCounter++, false);
        }
    }

    /**
     * Point
     * */
    public void ratOut(int challengeRound, Address maliciousUser){

        setEntrance();

        require(currentSubmission.get(challengeRound) != null
                && currentSubmission.get(challengeRound).get(maliciousUser) != null, "Ratout failed: "
        );

        boolean result = challengerResult.get(challengeRound);

        //if result was true and submiter validated then false rat out
        if(result && currentSubmission.get(challengeRound).get(maliciousUser)){

            require(false, "False Rat out");

        }else if(!result && !currentSubmission.get(challengeRound).get(maliciousUser)){

            require(false, "False Rat out");

        }else{

            increaseUserYellowCards(maliciousUser);

            Msg.sender().transfer(RAT_OUT_PAYOUT);

            if(yellowCard.get(Msg.sender()) != null && yellowCard.get(Msg.sender()) > 5){
                userBalance.put(maliciousUser, userBalance.get(maliciousUser).subtract(RAT_OUT_PAYOUT));
            }

            // delete data in order to prevent double submissions
            Map <Address, Boolean> b = currentSubmission.get(challengeRound);
            b.put(maliciousUser, result);
            currentSubmission.put(challengeRound, b);

        }

        setClosure();
    }

    /** MUTABLE NON-OWNER FUNCTIONS */



    public void submitOracleInfo(@Required Boolean feedbackPrice) {

        //Only allow submissions when not paused
        notPaused();

        // Feeder must have deposited at least min nuls to valid info inserted
        onlyIfFeederHasDeposit(Msg.sender());

        // Feeder must have at most 5 mistakes
        onlyIfValidYellowCards(Msg.sender());

        // Either a seeder or feeder must be a contract
        require((oracleSeedFillers.get(Msg.sender()) != null && oracleSeedFillers.get(Msg.sender()))
                ||  (Msg.sender().isContract() && oracleNormalFillers.get(Msg.sender())), "Feeder is seeder or contract");

        // Challenger must exist
        require(challenger != null, "Challenge not created");

        if(feedbackPrice){

            challengerApprovs += 1;

            approveChallenger();

        }else{

            challengerRejects += 1;

            rejectChallenger();
        }

        // Check to prevent withdraws until 2 daus after price submit
        lastUserSubmit.put(Msg.sender(), Block.timestamp());

        Map<Address, Boolean> c = currentSubmission.get(Msg.sender());
        c.put(Msg.sender(), feedbackPrice);
        currentSubmission.put(challengerCounter, c);


    }


    /**
     * Increase feeder yellow cards and expell him if yellow cards are
     * higher than 5.
     *
     * */
    private void increaseUserYellowCards(Address user){

        int yellowCards = yellowCard.get(user);

        yellowCards = (yellowCard.get(user) != null) ? yellowCards : 0;
        yellowCard.put(user, yellowCards + 1);

        int validN = validFeedinOracle;

        if(yellowCards + 1 > 5 && validN > 1){
            validFeedinOracle = validN - 1;
        }
    }

    /**
     * Submit challenger price that differs 1% from current price
     * or current price was submit 1 hour ago or more
     *
     * */
    @Payable
    public void submitOracleInfo(@Required BigInteger newPrice) {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();

        onlyIfFeederHasDeposit(Msg.sender());

        onlyIfValidYellowCards(Msg.sender());

        if(onlySeeders){

            //Require that feeder is seeder and there isn't a challenger active
            require(oracleSeedFillers.get(Msg.sender()), "Not Seeder");
            require(challenger == null, "Price in appreciation");

            // Variations of 1% up and down
            BigInteger allow1perDeltaPos = oracle.multiply(BASIS_POINTS).divide(BASIS_1PLUS);
            BigInteger allow1perDeltaNeg = oracle.multiply(BASIS_POINTS).divide(BASIS_1MINUS);

            // Require that last update was over 1 hour ago or price varation was higher than 1%
            require((oracleLastUpdated.add(ONE_HOUR)).compareTo(BigInteger.valueOf(Block.timestamp())) >=0
                    || (newPrice.compareTo(allow1perDeltaPos) > 0 && newPrice.compareTo(allow1perDeltaNeg) < 0)
                    , "Too soon");

            // Create new challenger, update challenge data and update last user submission
            challenger          =  newPrice;
            challengerOwner      = Msg.sender();
            challengerApprovs   = 1;


        }else {

            // verify that feeder is normal or seeder and that there is no challenger
            require(oracleNormalFillers.get(Msg.sender()) || oracleSeedFillers.get(Msg.sender()), "Not Seeder");
            require(challenger == null, "Price in appreciation");

            // Variations of 1% up and down
            BigInteger allow1perDeltaPos = oracle.multiply(BASIS_POINTS).divide(BASIS_1PLUS);
            BigInteger allow1perDeltaNeg = oracle.multiply(BASIS_POINTS).divide(BASIS_1MINUS);

            // Require that last update was over 1 hour ago or price varation was higher than 1%
            require((oracleLastUpdated.add(ONE_HOUR)).compareTo(BigInteger.valueOf(Block.timestamp())) >=0
                            || (newPrice.compareTo(allow1perDeltaPos) > 0 && newPrice.compareTo(allow1perDeltaNeg) < 0)
                    , "Too soon");

            // Create new challenger, update challenge data and update last user submission
            challenger          =  newPrice;
            challengerOwner      = Msg.sender();
            challengerApprovs   = 1;

        }

        lastUserSubmit.put(Msg.sender(), Block.timestamp());

        setClosure();

    }


    /**
     * Read Info from oracle
     *
     * Pay 0.1 NULS - 0.05 distributed to oracle token holders
     *              - 0.05 distributed to the oracle feeders
     *
     * */
    @Payable
    public String readInfo() {

        // revert all txs if this is paused, because pausing is only done in potential attacks
        notPaused();

        require(Msg.value().compareTo(pricePerRead) >= 0, "NulsOraclesV1: You need to pay");

        treasury.transfer(FIVEPER_NULS);

        return (    oracle != null) ?
                    oracle.toString()          + ",V1," + oracleLastUpdated.toString()
                :   BigInteger.ZERO.toString() + ",V1," + oracleLastUpdated.toString();

    }

    /**
     * Deposit funds on Oracle
     *
     * @dev required in order for be able to feed information
     * */
    @Payable
    public void depositOnBehalf() {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();

        BigInteger amount = Msg.value();

        if(userBalance.get(Msg.sender()) == null){
            userBalance.put(Msg.sender(), amount);
        }else{
            userBalance.put(Msg.sender(), userBalance.get(Msg.sender()).add(amount));
        }

        setClosure();

    }

    /**
     * Withdraw funds from Oracle
     *
     * */
    public void withdraw() {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();

        require(lastUserSubmit.get(Msg.sender()) + FIVE_DAYS <= Block.timestamp() , "Only allow withdraw after 5 days");

        BigInteger amount = Msg.value();

        if(userBalance.get(Msg.sender()) == null){
            userBalance.put(Msg.sender(), amount);
        }else{
            userBalance.put(Msg.sender(), userBalance.get(Msg.sender()).subtract(amount));
        }

        setClosure();

    }

    //--------------------------------------------------------------------
    /** MUTABLE OWNER FUNCTIONS */

    /**
     * Add new Admin to Oracle
     *
     * */
    public void addAdmin(Address newAdmin){

        onlyAdmin();

        projectAdmin.put(newAdmin, true);

    }

    /**
     *
     *
     * */
    public void removeAdmin(Address removeAdmin){

        onlyAdmin();
        require(!Msg.sender().equals(removeAdmin), "Can't remove itself");

        projectAdmin.put(removeAdmin, false);

    }

    /**
     *
     *
     * */
    public void cleanYellowCards(Address addr){

        onlyAdmin();

        yellowCard.put(addr, 0);

    }

    public void claimLeftOvers(Address recipient){

        onlyAdmin();

        recipient.transfer(penaltiesLeftOver);
        penaltiesLeftOver = BigInteger.ZERO;

    }

    public void setPaused(){
        onlyAdmin();
        paused = true;
    }

    public void setUnpaused(){
        onlyAdmin();
        paused = false;
    }

    /** Essential to receive funds back from aiNULS
     *
     * @dev DON'T REMOVE IT,
     *      if you do you will be unable to withdraw from aiNULS
     */
    @Payable
    public void _payable() {

    }

    //--------------------------------------------------------------------
    /** INTERNAL FUNCTIONS */

    private void safeTransfer(@Required Address token, @Required Address recipient, @Required BigInteger amount){
        String[][] argsM = new String[][]{new String[]{recipient.toString()}, new String[]{amount.toString()}};
        boolean b = new Boolean(token.callWithReturnValue("transfer", "", argsM, BigInteger.ZERO));
        require(b, "NEII-V1: Failed to transfer");
    }

    private void safeTransferFrom(@Required Address token, @Required Address from, @Required Address recipient, @Required BigInteger amount){
        String[][] args = new String[][]{new String[]{from.toString()}, new String[]{recipient.toString()}, new String[]{amount.toString()}};
        boolean b = new Boolean(token.callWithReturnValue("transferFrom", "", args, BigInteger.ZERO));
        require(b, "NulswapV1: Failed to transfer");
    }

}