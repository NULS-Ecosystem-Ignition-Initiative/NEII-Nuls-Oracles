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

public class Challenge(){

    public BigInteger challenger ;         // Challenger price to current
    public Address challengerOwner       // Challenger price to current
    public Integer challengerApprovs       // Challenger price to current
    public Integer challengerRejects     // Challenger price to current

    public Challenge(){

    }
}

public class Oracle{

    public BigInteger oracle;         // Oracle that stores price info
    public BigInteger challenger ;         // Challenger price to current
    public Address challengerOwner       // Challenger price to current
    public Integer challengerApprovs       // Challenger price to current
    public Integer challengerRejects     // Challenger price to current
    public Booelan onlySeeders            // If true only seeders can submit
    public BigInteger oracleLastUpdated   // When was the last oracle update
    public Integer validFeedinOracle   // number of approved feeders

    public Map<Address, Boolean> admins = new HashMap<>();
    public Map<Address, Boolean> oracleSeedFillers = new HashMap<>();// check if is seed in oracle
    public Map<Address, Integer> currentSubmission = new HashMap<>(); //



    public Oracle(BigInteger oracle,
                  BigInteger challenger,
                  Address challengerOwner,
                  ){
        this.oracle = oracle;
        this.challenger = challenger;
        this.challengerOwner

    }
}

/**
* @notice Nuls Contract that locks the nuls deposited, returns
* yield to a project during an x period of time and
* returns nuls locked in the end of the period
*
* @dev Nuls are deposited in AINULS in order to receive yield
*
* Developed by Pedro G. S. Ferreira @Pedro_Ferreir_a
* */
public class NulsOracles extends ReentrancyGuard implements Contract{

    /** 100 NULS
     *   @dev Min required to deposit in aiNULS is 100 NULS
     */
    private static final BigInteger ONE_NULS     = BigInteger.valueOf(100000000L);
    private static final BigInteger FIVEPER_NULS     = BigInteger.valueOf(50000000L);
    private static final BigInteger TWO          = BigInteger.valueOf(2);
    private static final BigInteger FEE_NULS     = BigInteger.valueOf(10000000L);
    private static final BigInteger SLASH_FEE    = BigInteger.valueOf(1000000000L);
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    private static final BigInteger ONE_HOUR     = BigInteger.valueOf(60 * 60);
    private static final BigInteger RAT_OUT_PAYOUT  = BigInteger.valueOf(500000000L);
    private static final BigInteger INACTIVE_PAYOUT  = BigInteger.valueOf(10000000L);

    public Address token; // Project Token
    private BigInteger tokenTotalSupply;

    public Boolean paused;

    public BigInteger minNULSForFeeder;
    public BigInteger minValids; // Min number of submissions required for filling info in oracle
    public BigInteger pricePerRead;
    public BigInteger priceForFeederValid;
    public BigInteger penaltiesLeftOver; //penalties charged for fillers non compliant
    public Address treasury;

    public Integer oracleCounter;

    //User Balance
    public Map<Address, BigInteger> userBalance        = new HashMap<>(); // Amount deposited to check if can fill oracle needs
    public BigInteger oracle; //price of asset
    public BigInteger challenger; // Challenger price to current
    public Address challengerOwner; // Challenger price to current
    public Integer challengerApprovs      ; // Challenger price to current
    public Integer challengerRejects    ; // Challenger price to current
    public Boolean onlySeeders           = new ArrayList<>(); // If true only seeders can submit
    public BigInteger oracleLastUpdated  ; // When was the last oracle update
    public Integer validFeedinOracle; // number of approved feeders
    public Map<Address, BigInteger> minValidationsToSubmit       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable

    public  Map<Address, Long> firstSubmissionToOracle    = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable


    public Map<Address, Integer> yellowCard       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable

    public Map<Address, Long> lastUserSubmit       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable

    public  Map<Address, Boolean> admins = new HashMap<>();
    public  Map<Address, Boolean> oracleSeedFillers; // check if is seed in oracle
    public Map<Address, Boolean> oracleNormalFillers; // check if is seed in oracle
    public Map<Integer, Map<Address, Boolean>> currentSubmission; //results of the challenge


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
                       @Required treasury_

    ) {
        // Min nuls to submit info needs to pay the yellow card payments
        require(minNULSForFeeder_.compareTo(RAT_OUT_PAYOUT * 5), "");

        admins.put(admin_, true);
        paused = false;
        minNULSForFeeder = minNULSForFeeder_;
        pricePerRead = pricePerRead_;
        token = token_;
        minValids = minValids_;
        priceForFeederValid = priceForFeederValid_;
        treasury = treasury_;
        penaltiesLeftOver = BigInteger.ZERO;

        tokenTotalSupply = new BigInteger(token.callWithReturnValue("totalSupply", "", null, BigInteger.ZERO));

        challengerCounter = 0;
        oracleCounter = 0;
    }

    /** VIEW FUNCTIONS */

    /**
     * @notice Get aiNULS asset address
     *
     * @return aiNULS Token Contract Address
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
    public Boolean isAdmin(@Required int number,Address admin) {
        if(projectAdmin.get(number) == null){
            return false;
        }else if(projectAdmin.get(number).get(admin) == null){
            return false;
        }
        return projectAdmin.get(number).get(admin);
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
    public Boolean newChallenger(BigInteger oracleNumber){
        if(challenger.get(oracleNumber) != null)
            return true;
        return false;
    }


    @View
    public Boolean isPaused(){
        return paused;
    }

    /** MODIFIER FUNCTIONS */

    protected void onlyAdmin(){
        require(projectAdmin.get(Msg.sender()) != null && projectAdmin.get(Msg.sender()), "Invalid Admin");
    }

    protected void notPaused(){
        require(!paused, "");
    }


    protected void onlyIfFeederHasDeposit(Address feeder){
        require(userBalance.get(feeder).compareTo(minNULSForFeeder) >= 0, "NulsOracleV1: Min Nuls Required to Submit");
    }

    protected void onlyIfValidYellowCards(Address feeder){
        require(yellowCard.get(feeder) <= 5, "Expelled from feeders");
    }

    public void openOracleToPublic(){
        onlyAdmin();
        onlySeeders = false;
    }

    @Payable
    public void enterNewFeeder(){

        require(Msg.value().compareTo(RAT_OUT_PAYOUT.multiply(5)), "Pay for payouts");
        firstSubmissionToOracle.put(Msg.sender(), true);
    }

    public void completeProcess(int oracleNumber, int seedersNumber){

        require(firstSubmissionToOracle.get(Msg.sender()) != null, "Process is null");
        require(firstSubmissionToOracle.get(Msg.sender) + TWO_DAYS < Block.tomestamp(), "Two days waiting period");

        validFeedinOracle += 1;
        oracleNormalFillers.put(Msg.sender(), true);
    }


    public void iAmActive(){
        // Check to prevent withdraws until 2 daus after price submit and to check that oracle is active
        lastUserSubmit.put(Msg.sender(), Block.timestamp());
    }

    public void alertInactive( Address inactiveUser){

        require(validFeedinOracle > 1, "Last filler is always right");

        Boolean b = oracleNormalFillers.get(inactiveUser);
        require(b != null && b, "Not feeder");

        require((lastUserSubmit.get(inactiveUser).add(TWO_DAYS)).compareTo(Block.timestramp()))

        oracleNormalFillers.put(inactiveUser, false);
        validFeedinOracle.put(oracleNumber, validFeedinOracle - 1);
        Msg.sender().tranfer(INACTIVE_PAYOUT);
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

            if(yellowCard > 5){
                userBalance.put(maliciousUser, userBalance.get(maliciousUser).subtreact(RAT_OUT_PAYOUT));
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
        require((oracleSeedFillers.get(Msg.sender()) != null && oracleSeedFillers.get(Msg.sender()) || Msg.sender().isContract(), "Feeder is seeder or contract");

        // Challenger must exist
        require(challenger != null, "Challenge not created");

        if(feedbackPrice){

            challengerApprovs = challengerApprovs  + 1;

            if(challengerApprovs >= (validFeedinOracle / 2 + 1)){

                oracle            = challenger;
                oracleLastUpdated =  BigInteger.valueOf(Block.timestamp());
                challengerApprovs = 0;
                challengerRejects = 0;
                challenger        = null;
                challengerResult.put(challengerCounter, true);

            }
        }else{
                challengerRejects += 1;
                if(challengerRejects >= validFeedinOracle / 2 + 1)){

                    increaseUserYellowCards(challengerOwner);

                    challengerApprovs = 0;
                    challengerRejects = 0;
                    challenger = null;
                    challengerResult.put(challengerCounter, false);
                }


            }

        // Check to prevent withdraws until 2 daus after price submit
            lastUserSubmit.put(Msg.sender(), Block.timestamp());

            Map<Address, Boolean>> c;
            c.put(Msg.sender(), feedbackPrice);
            currentSubmission.put(challengerCounter, c);


    }



    private void increaseUserYellowCards(Address user){
            int yellowCards = yellowCard.get(user);
            yellowCards = (yellowCards != null) ? yellowCards : 0;
            yellowCard.put(user, yellowCards + 1);

            int validN = validFeedinOracle;

            if(yellowCards + 1 > 5 && validN > 1){
                validFeedinOracle = validN - 1;
            }
    }

    /**
     * Deposit funds on Lock
     *
     * */
    @Payable
    public void submitOracleInfo(@Required BigInteger oracleNumber, BigInteger newPrice) {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();

        onlyIfFeederHasDeposit(Msg.sender());

        onlyIfValidYellowCards(Msg.sender());

        if(onlySeeders){

            require(oracleSeedFillers.get(Msg.sender()), "Not Seeder");
            require(challenger == null, "Price in appreciation");


            BigInteger allow1perDeltaPos = oracle.get(oracleNumber).multiply(BASIS_POINTS).divide(10100);
            BigInteger allow1perDeltaNeg = oracle.get(oracleNumber).multiply(BASIS_POINTS).divide(9900);
            require((oracleLastUpdated.get(oracleNumber).add(ONE_HOUR)).compareTo(Block.timestamp()) >=0
                    || (newPrice.compareTo(allow1perDeltaPos) > 0 && newPrice.compareTo(allow1perDeltaNeg)) < 0
                    , "Too soon");

            challenger =  newPrice;
            challengeOwner = Msg.sender();
            challengerApprovs = 1;
            lastUserSubmit.put(Msg.sender(), Block.timestamp());

        }else {

            BigInteger userValids = minValidationsToSubmit.get(Msg.sender());

            if (userValids != null
                    && minValids.compareTo(userValids) > 0) {

                safeTransferFrom(token, Msg.sender(), Msg.address(), priceForFeederValid);

                require(Msg.value().compareTo(FEE_NULS) >= 0, "NulsOracleV1: Noobs Must Pay");

                treasury.transfer(FEE_NULS);

                minValidationsToSubmit.put(Msg.sender(), userValids.add(BigInteger.ONE));
                if (minValids.compareTo(userValids) == 0)
                    validFeedinOracle += 1;
            } else if (minValids.compareTo(userValids) <= 0) {

                BigInteger price = oracle;

                if (challenger == null) {
                    challenger = newPrice;
                    challengerApprovs = 1;
                } else {


                    // see if it is between 1% and
                    BigInteger allow1perDeltaPos = newPrice.multiply(BASIS_POINTS).divide(10100);
                    BigInteger allow1perDeltaNeg = newPrice.multiply(BASIS_POINTS).divide(9900);
                    if (challenger.compareTo(allow1perDeltaPos) <= 0 && challenger.compareTo(allow1perDeltaNeg) >= 0) {
                        challengerApprovs +=1;

                        if(validFeedinOracle)
                            oracleLastUpdated =  BigInteger.valueOf(Block.timestamo());
                    }
                }

            } else {
                minValidationsToSubmit.put(Msg.sender(), BigInteger.ONE);
            }
        }



        setClosure();

    }



    @Payable
    public String readInfo(@Required BigInteger oracleNumber) {

        require(Msg.value().compareTo(pricePerRead) >= 0, "NulsOraclesV1: You need to pay");

        treasury.transfer(FIVEPER_NULS);

        return (    oracle != null) ?
                    oracle.toString()          + ",V1," + oracleLastUpdated.toString()
                :   BigInteger.ZERO.toString() + ",V1," + oracleLastUpdated.toString();

    }

    /**
     * Deposit funds on Lock
     *
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
     * Deposit funds on Lock
     *
     * */
    public void withdraw() {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();

        require(lastUserSubmit.get(Msg.sender()).compareTo(Block.timestamp()).add(TWO_DAYS), "");

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

    public void addAdmin(Address newAdmin){

        onlyAdmin();

        projectAdmin.put(newAdmin, true);

    }

    public void addOracleFiller(int oracle, Address newFiller){
        Map<Address, Boolean> a = new HashMap<>();

        a.put(admin_, true);
        oracleFillers.put(oracle, a);
    }

    public void removeAdmin(Address removeAdmin){

        onlyAdmin();
        require(!Msg.sender().equals(removeAdmin), "Can't remove itself");

        projectAdmin.put(removeAdmin, false);

    }

    public void cleanYellowCards(Address addr){

        onlyAdmin();

        yellowCard.put(addd, 0);

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

    private BigInteger getBalAINULS(@Required Address owner){
        String[][] args = new String[][]{new String[]{owner.toString()}};
        BigInteger b = new BigInteger(depositCtr.callWithReturnValue("balanceOf", "", args, BigInteger.ZERO));
        return b;
    }

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