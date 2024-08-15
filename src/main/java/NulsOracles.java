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

    public Map<Integer, Map<Address, Boolean>> admins = new HashMap<>();
    public Map<Integer, Map<Address, Boolean>> oracleSeedFillers = new HashMap<>();// check if is seed in oracle
    public Map<Integer, Map<Address, Integer>> currentSubmission = new HashMap<>(); //



    public Oracle(){

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
    private static final BigInteger TWO          = BigInteger.valueOf(2);
    private static final BigInteger FEE_NULS     = BigInteger.valueOf(10000000L);
    private static final BigInteger SLASH_FEE    = BigInteger.valueOf(1000000000L);
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    private static final BigInteger ONE_HOUR     = BigInteger.valueOf(60 * 60);
    private static final BigInteger RAT_OUT_PAYOUT  = BigInteger.valueOf(500000000L);

    public Address token; // Project Token

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
    public Map<Integer, BigInteger> oracle             = new HashMap<>(); // Oracle that stores price info
    public Map<Integer, BigInteger> challenger         = new HashMap<>(); // Challenger price to current
    public Map<Integer, Address> challengerOwner        = new HashMap<>(); // Challenger price to current
    public Map<Integer, Integer> challengerApprovs      = new HashMap<>(); // Challenger price to current
    public Map<Integer, Integer> challengerRejects     = new HashMap<>(); // Challenger price to current
    public Map<Integer, Boolean> onlySeeders           = new HashMap<>(); // If true only seeders can submit
    public Map<Integer, BigInteger> oracleLastUpdated  = new HashMap<>(); // When was the last oracle update
    public Map<Integer, Integer> validFeedinOracle  = new HashMap<>(); // number of approved feeders
    public Map<Address, BigInteger> minValidationsToSubmit       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable


    public Map<Address, Integer> yellowCard       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable

    public Map<Address, Long> lastUserSubmit       = new HashMap<>(); // Min Valids that a feeder must submit to info be considered reliable

    public Map<Integer, Map<Address, Boolean>> admins = new HashMap<>();
    public Map<Integer, Map<Address, Boolean>> oracleSeedFillers = new HashMap<>();// check if is seed in oracle
    public Map<Integer, Map<Address, Boolean>> currentSubmission = new HashMap<>(); //results of the challenge

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

    public void onlyAdmin(){
        require(projectAdmin.get(Msg.sender()) != null && projectAdmin.get(Msg.sender()), "Invalid Admin");
    }

    public void notPaused(){
        require(!paused, "");
    }


    public void enterNewOracle(int seedersNumber){

        challengerApprovs.put(oracleCounter, 0);
        challengerRejects.put(oracleCounter, 0);
        validFeedinOracle.put(oracleCounter, seedersNumber);
        onlySeeders.put(oracleCounter++, true);
    }

    public void openOracle(int oraclenumber){
        require(admins.get(oraclenumber).get(Msg.sender()), "Not Oracle Admin");
        onlySeeders.put(oraclenumber, false);
    }

    public void ratOut(int oracleNumber, Address maliciousUser){

        setEntrance();


        require(currentSubmission.get(oracleNumber) != null
                && currentSubmission.get(oracleNumber).get(maliciousUser) != null
                && currentSubmission.get(oracleNumber).get(maliciousUser), ""
        );
        require(challengerResult.get(oracleNumber), "");

        yellowCard.put(maliciousUser, yellowCard.get(maliciousUser) ? yellowCard.get(maliciousUser) + 1 : 1);
        Msg.sender().transfer(RAT_OUT_PAYOUT);

        if(yellowCard > 5){
            userBalance.put(maliciousUser, userBalance.get(maliciousUser).subtreact(RAT_OUT_PAYOUT));
        }

        setClosure();
    }

    /** MUTABLE NON-OWNER FUNCTIONS */

    @Payable
    public void submitOracleInfo(@Required BigInteger oracleNumber, Boolean feedbackPrice) {

        require(oracleNumber < oracleCounter, "Impossible to feed the future");

        //Only allow locks when not paused
        notPaused();

        require(userBalance.get(Msg.sender()).compareTo(minNULSForFeeder) >= 0, "NulsOracleV1: Min Nuls Required to Submit");
        require(yellowCard.get(Msg.sender()) <= 5, "Expelled from feeders");
        require(onlySeeders.get(oracleNumber) || minValidationsToSubmit.get())
        require(challenger.get(oracleNumber) != null, "Challenge not created");

        if(onlySeeders.get(oracleNumber)){
            require(oracleSeedFillers.get(oracleNumber).get(Msg.sender()), "Not Seeder");
        }

        if(feedbackPrice){
            challengerApprovs.put(oracleNumber, challengerApprovs.get(oracleNumber) + 1);
            if(challengerApprovs.get(oracleNumber).compareTo(BigInteger.valueOf(validFeedinOracle.get(oracleNumber) / 2 + 1)) )>=0){
                oracle.put(oracleNumber, challenger.get(oracleNumber));
                oracleLastUpdated.put(oracleNumber, BigInteger.valueOf(Block.timestamp()));
                challengerApprovs.put(oracleNumber, 0);
                challengerRejects.put(oracleNumber, 0);
                challenger.put(oracleNumber, null);
                challengerResult.put(oracleNumber, true);
            }
        }else{
                challengerRejects.put(oracleNumber, challengerRejects.get(oracleNumber) + 1);
                if(challengerRejects.get(oracleNumber).compareTo(BigInteger.valueOf(validFeedinOracle.get(oracleNumber) / 2 + 1)) )>= 0){

                    increaseUserYellowCards(oracleNumber);

                    challengerApprovs.put(oracleNumber, 0);
                    challengerRejects.put(oracleNumber, 0);
                    challenger.put(oracleNumber, null);
                challengerResult.put(oracleNumber, false);
                }


            }

        // Check to prevent withdraws until 2 daus after price submit
            lastUserSubmit.put(Msg.sender(), Block.timestamp());

        Map<Address, Boolean>> c;
        c.put(Msg.sender(), feedbackPrice);

                currentSubmission.put(oracleNumber, c);
    }

    private void increaseUserYellowCards(BigInteger oracleNumber){
            Address user = challengerOwner.get(oracleNumber);
            int yellowCards = yellowCard.get(user);
            yellowCards = (yellowCards != null) ? yellowCards : 0;
            yellowCard.put(user, yellowCards + 1);

            int validN = validFeedinOracle.get(oracleNumber);

            if(yellowCards + 1 > 5 && validN != null  && validN > 1){
                validFeedinOracle.put(oracleNumber, validN - 1);
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

        require(oracleNumber < oracleCounter, "Impossible to feed the future");
        require(userBalance.get(Msg.sender()).compareTo(minNULSForFeeder) >= 0, "NulsOracleV1: Min Nuls Required to Submit");
        require(yellowCard.get(Msg.sender()) <= 5, "Expelled from feeders");

        if(onlySeeders.get(oracleNumber)){

            BigInteger challenge = challenger.get(oracleNumber);

            require(oracleSeedFillers.get(oracleNumber).get(Msg.sender()), "Not Seeder");
            require(challenge == null, "Price in appreciation");


            BigInteger allow1perDeltaPos = oracle.get(oracleNumber).multiply(BASIS_POINTS).divide(10100);
            BigInteger allow1perDeltaNeg = oracle.get(oracleNumber).multiply(BASIS_POINTS).divide(9900);
            require((oracleLastUpdated.get(oracleNumber).add(ONE_HOUR)).compareTo(Block.timestamp()) >=0
                    || (newPrice.compareTo(allow1perDeltaPos) > 0 && newPrice.compareTo(allow1perDeltaNeg)) < 0
                    , "Too soon");

            challenge.put(oracleNumber, newPrice);
            challengeOwner.put(oracleNumber, Msg.sender());
            challengerApprovs.put(oracleNumber, 1);
            lastUserSubmit.put(Msg.sender(), Block.timestamp());

        }else {

            BigInteger userValids = minValidationsToSubmit.get(Msg.sender());

            if (userValids != null
                    && minValids.compareTo(userValids) > 0) {

                safeTransferFrom(token, Msg.sender(), Msg.address(), priceForFeederValid);

                require(Msg.value().compareTo(FEE_NULS) >= 0, "NulsOracleV1: Noobs Must Pay");

                treasury.transfer(FEE_NULS);

                minValidationsToSubmit.put(Msg.sender(), userValids.add(BigInteger.ONE));
                if (minValids.compareTo(userValids) == 0 && validFeedinOracle.get(oracleNumber) != null)
                    validFeedinOracle.put(oracleNumber, validFeedinOracle.get(oracleNumber).add(BigInteger.ONE));
            } else if (minValids.compareTo(userValids) <= 0) {
                BigInteger price = oracle.get(oracleNumber);

                BigInteger challenge = challenger.get(oracleNumber);

                if (challenge == null) {
                    challenger.put(oracleNumber, newPrice);
                    challengerApprovs.put(oracleNumber, BigInteger.ONE);
                } else {


                    // see if it is between 1% and
                    BigInteger allow1perDeltaPos = newPrice.multiply(BASIS_POINTS).divide(10100);
                    BigInteger allow1perDeltaNeg = newPrice.multiply(BASIS_POINTS).divide(9900);
                    if (challenge.compareTo(allow1perDeltaPos) <= 0 && challenge.compareTo(allow1perDeltaNeg) >= 0) {
                        challengerApprovs.put(oracleNumber, challengerApprovs.get(oracleNumber) + 1);

                        if(validFeedinOracle)
                            oracleLastUpdated.put(oracleNumber, BigInteger.valueOf(Block.timestamo()));
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

        return (oracle.get(oracleNumber) != null) ?
                    oracle.get(oracleNumber).toString() + ",V1," + oracleLastUpdated.get(oracleNumber).toString()
                :   BigInteger.ZERO.toString()          + ",V1," + oracleLastUpdated.get(oracleNumber).toString();

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


        oracleLastUpdated.put(oracleNumber, BigInteger.valueOf(Block.timestamo()));

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