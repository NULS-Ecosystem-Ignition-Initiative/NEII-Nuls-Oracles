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
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);

    public Address depositCtr;
    public Address treasury; // Address that will receive the project NULS
    public Address token; // Project Token

    public Boolean paused;

    public BigInteger minNULSForFeeder;
    public BigInteger pricePerRead;

    public Long oracleCounter;

    //User Balance
    public Map<Address, BigInteger> userBalance        = new HashMap<>();
    public Map<Long, BigInteger>    oracle             = new HashMap<>();
    public Map<Long, BigInteger>    oracleLastUpdated  = new HashMap<>();

    public Map<Long Map<Address, Boolean>> projectAdmin = new HashMap<>();

    //--------------------------------------------------------------------
    //Initialize Contract
    public NulsOracles(@Required BigInteger pricePerRead_,
                     @Required BigInteger minNULSForFeeder_,
                     @Required BigInteger minNULSForFeeder_,
                     @Required Address token_,
                     @Required Address admin_

    ) {


        treasury = treasury_;
        projectAdmin.put(admin_, true);
        paused = false;
        minNULSForFeeder = minNULSForFeeder_;
        pricePerRead = pricePerRead_;
        token = token_;

        oracleCounter;
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
    public Boolean isAdmin(Address admin) {
        if(projectAdmin.get(admin) == null)
            return false;
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

    /** MUTABLE NON-OWNER FUNCTIONS */

    /**
     * Deposit funds on Lock
     *
     * */
    @Payable
    public void submitOracleInfo(@Required BigInteger oracleNumber, BigInteger amount) {

        //Prevent Reentrancy Attacks
        setEntrance();

        //Only allow locks when not paused
        notPaused();



        oracleLastUpdated.put(oracleNumber, BigInteger.valueOf(Block.timestamo()));

        setClosure();

    }

    @Payable
    public String readInfo(@Required BigInteger oracleNumber) {

        require(Msg.value().compareTo(pricePerRead) >= 0, "NulsOraclesV1: You need to pay");

        return (oracle.get(oracleNumber) != null) ?
                    oracle.get(oracleNumber).toString() + ",V1," + oracleLastUpdated.get(oracleNumber).toString()
                :   BigInteger.ZERO.toString()          + ",V1," + oracleLastUpdated.get(oracleNumber).toString();

    }

    //--------------------------------------------------------------------
    /** MUTABLE OWNER FUNCTIONS */

    public void addAdmin(Address newAdmin){

        onlyAdmin();

        projectAdmin.put(newAdmin, true);

    }

    public void removeAdmin(Address removeAdmin){

        onlyAdmin();
        require(!Msg.sender().equals(removeAdmin), "Can't remove itself");

        projectAdmin.put(removeAdmin, false);

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

}