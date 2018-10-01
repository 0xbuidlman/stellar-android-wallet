package blockeq.com.stellarwallet.services.networking

import android.os.AsyncTask
import android.util.Log
import blockeq.com.stellarwallet.WalletApplication
import blockeq.com.stellarwallet.helpers.Constants
import blockeq.com.stellarwallet.interfaces.OnLoadAccount
import blockeq.com.stellarwallet.interfaces.OnLoadEffects
import blockeq.com.stellarwallet.interfaces.SuccessErrorCallback
import blockeq.com.stellarwallet.utils.StringFormat
import org.stellar.sdk.*
import org.stellar.sdk.requests.ErrorResponse
import org.stellar.sdk.requests.RequestBuilder
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.Page
import org.stellar.sdk.responses.effects.EffectResponse
import java.util.*


class Horizon {
    companion object {
        const val PROD_SERVER = "https://horizon.stellar.org"
        const val TEST_SERVER = "https://horizon-testnet.stellar.org"

        private val TAG = Horizon::class.java.simpleName

        class LoadAccountTask(private val listener: OnLoadAccount) : AsyncTask<Void, Void, AccountResponse>() {
            override fun doInBackground(vararg params: Void?) : AccountResponse? {
                val server = Server(PROD_SERVER)
                var account : AccountResponse? = null
                try {
                    account = server.accounts().account(WalletApplication.session!!.keyPair)

                } catch (error : ErrorResponse) {
                    Log.d(TAG, error.body.toString())
                }

                return account
            }

            override fun onPostExecute(result: AccountResponse?) {
                listener.onLoadAccount(result)
            }
        }

        class LoadEffectsTask(private val listener: OnLoadEffects) : AsyncTask<Void, Void, ArrayList<EffectResponse>?>() {
            override fun doInBackground(vararg params: Void?): ArrayList<EffectResponse>? {
                val server = Server(PROD_SERVER)
                var effectResults : Page<EffectResponse>? = null
                try {
                    effectResults = server.effects().order(RequestBuilder.Order.DESC)
                            .forAccount(WalletApplication.session!!.keyPair).execute()
                } catch (error : ErrorResponse) {
                    Log.d(TAG, error.body.toString())
                }

                return effectResults?.records
            }

            override fun onPostExecute(result: ArrayList<EffectResponse>?) {
                listener.onLoadEffects(result)
            }

        }

        class SendTask(private val listener: SuccessErrorCallback, private val destAddress: String,
                       private val memo: String, private val amount : String) : AsyncTask<Void, Void, ErrorResponse>() {

            override fun doInBackground(vararg params: Void?): ErrorResponse? {
                val sourceKeyPair = WalletApplication.session!!.keyPair
                val server = Server(PROD_SERVER)
                val destKeyPair = KeyPair.fromAccountId(destAddress)

                Network.usePublicNetwork()

                try {
                    server.accounts().account(destKeyPair)

                    val sourceAccount = server.accounts().account(sourceKeyPair)

                    val transaction = Transaction.Builder(sourceAccount)
                            .addOperation(PaymentOperation.Builder(destKeyPair, AssetTypeNative(), amount).build())
                            // A memo allows you to add your own metadata to a transaction. It's
                            // optional and does not affect how Stellar treats the transaction.
                            .addMemo(Memo.text(memo))
                            .build()

                    transaction.sign(sourceKeyPair)

                    server.submitTransaction(transaction)

                } catch (error : ErrorResponse) {
                    Log.d(TAG, error.body.toString())
                    return error
                }

                return null
            }

            override fun onPostExecute(result: ErrorResponse?) {
                if (result != null) {
                    listener.onError()
                } else {
                    listener.onSuccess()
                }
            }
        }

        class JoinInflationDestination(private val listener: SuccessErrorCallback,
                                       private val inflationDest : String)
            : AsyncTask<Void, Void, ErrorResponse>() {

            override fun doInBackground(vararg params: Void?): ErrorResponse? {
                Network.usePublicNetwork()

                val server = Server(PROD_SERVER)
                val sourceKeyPair = WalletApplication.session!!.keyPair
                val destKeyPair = KeyPair.fromAccountId(inflationDest)

                try {
                    val sourceAccount = server.accounts().account(sourceKeyPair)

                    val transaction = Transaction.Builder(sourceAccount)
                            .addOperation(SetOptionsOperation.Builder()
                                    .setInflationDestination(destKeyPair)
                                    .build())
                            .build()

                    transaction.sign(sourceKeyPair)
                    server.submitTransaction(transaction)

                } catch (error : ErrorResponse) {
                    Log.d(TAG, error.body.toString())
                    return error
                }
                return null
            }

            override fun onPostExecute(result: ErrorResponse?) {
                if (result != null) {
                    listener.onError()
                } else {
                    listener.onSuccess()
                }
            }
        }

        class ChangeTrust(private val listener: SuccessErrorCallback, private val asset: Asset,
                          private val removeTrust: Boolean)
            : AsyncTask<Void, Void, ErrorResponse>() {

            override fun doInBackground(vararg params: Void?): ErrorResponse? {
                Network.usePublicNetwork()

                val server = Server(PROD_SERVER)
                val sourceKeyPair = WalletApplication.session!!.keyPair
                val limit = if (removeTrust) "0.0000000" else Constants.MAX_ASSET_STRING_VALUE

                try {
                    val sourceAccount = server.accounts().account(sourceKeyPair)

                    val transaction = Transaction.Builder(sourceAccount)
                            .addOperation(ChangeTrustOperation.Builder(asset, limit).build())
                            .build()

                    transaction.sign(sourceKeyPair)
                    val response = server.submitTransaction(transaction)

                    if (!response.isSuccess) {
                        return ErrorResponse(0, "Response Error")
                    }

                } catch (error : ErrorResponse) {
                    Log.d(TAG, error.body.toString())
                    return error
                }
                return null
            }

            override fun onPostExecute(result: ErrorResponse?) {
                if (result != null) {
                    listener.onError()
                } else {
                    listener.onSuccess()
                }
            }
        }

        // TODO: Refactor When switching assets, get the right balance for asset, using AccountUtils
        fun getBalance() : String {
            WalletApplication.localStore!!.balances?.forEach {
                if (it.assetType == Constants.LUMENS_ASSET_TYPE) {
                    return StringFormat.truncateDecimalPlaces(it.balance)
                }
            }
            return Constants.DEFAULT_ACCOUNT_BALANCE
        }
    }
}
