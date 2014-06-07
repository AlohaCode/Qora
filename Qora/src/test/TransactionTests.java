package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;

import ntp.NTP;

import org.junit.Test;

import database.DatabaseSet;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.crypto.Crypto;
import qora.crypto.Ed25519;
import qora.naming.Name;
import qora.naming.NameSale;
import qora.transaction.BuyNameTransaction;
import qora.transaction.CancelSellNameTransaction;
import qora.transaction.GenesisTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.RegisterNameTransaction;
import qora.transaction.SellNameTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import qora.transaction.UpdateNameTransaction;

public class TransactionTests {

	//GENESIS
	
	@Test
	public void validateSignatureGenesisTransaction() 
	{
		Ed25519.load();
		
		//CHECK VALID SIGNATURE
		Transaction transaction = new GenesisTransaction(new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g"), BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		assertEquals(true, transaction.isSignatureValid());
	}
	
	@Test
	public void validateGenesisTransaction() 
	{
		Ed25519.load();
		
		//CREATE MEMORYDB
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
		
		//CHECK NORMAL VALID
		Transaction transaction = new GenesisTransaction(new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g"), BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		assertEquals(Transaction.VALIDATE_OKE, transaction.isValid(databaseSet));
		
		//CHECK INVALID ADDRESS
		transaction = new GenesisTransaction(new Account("test"), BigDecimal.valueOf(-1000).setScale(8), NTP.getTime());
		assertNotEquals(Transaction.VALIDATE_OKE, transaction.isValid(databaseSet));
		
		//CHECK NEGATIVE AMOUNT
		transaction = new GenesisTransaction(new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g"), BigDecimal.valueOf(-1000).setScale(8), NTP.getTime());
		assertNotEquals(Transaction.VALIDATE_OKE, transaction.isValid(databaseSet));
	}
	
	@Test
	public void parseGenesisTransaction() 
	{
		//CREATE TRANSACTION
		Account account = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		Transaction transaction = new GenesisTransaction(account, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		
		//CONVERT TO BYTES
		byte[] rawTransaction = transaction.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			Transaction parsedTransaction = TransactionFactory.getInstance().parse(rawTransaction);
			
			//CHECK INSTANCE
			assertEquals(true, parsedTransaction instanceof GenesisTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(transaction.getSignature(), parsedTransaction.getSignature()));
			
			//CHECK AMOUNT
			assertEquals(transaction.getAmount(account), parsedTransaction.getAmount(account));			
			
			//CHECK TIMESTAMP
			assertEquals(transaction.getTimestamp(), parsedTransaction.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawTransaction = new byte[transaction.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawTransaction);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}
	
	@Test
	public void processGenesisTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
		
		//PROCESS TRANSACTION
		Account account = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		Transaction transaction = new GenesisTransaction(account, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CHECK AMOUNT
		assertEquals(BigDecimal.valueOf(1000).setScale(8), account.getConfirmedBalance(databaseSet));
		
		//CHECK REFERENCE
		assertEquals(true, Arrays.equals(transaction.getSignature(), account.getLastReference(databaseSet)));
	}
	
	@Test
	public void orphanGenesisTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//PROCESS TRANSACTION
		Account account = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		Transaction transaction = new GenesisTransaction(account, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//ORPHAN TRANSACTION
		transaction.orphan(databaseSet);
		
		//CHECK AMOUNT
		assertEquals(BigDecimal.ZERO, account.getConfirmedBalance(databaseSet));
				
		//CHECK REFERENCE
		assertEquals(true, Arrays.equals(new byte[0], account.getLastReference(databaseSet)));
	}
	
	//PAYMENT
	
	@Test
	public void validateSignaturePaymentTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		Account recipient = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		long timestamp = NTP.getTime();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
		
		//CREATE PAYMENT
		Transaction payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF PAYMENT SIGNATURE IS VALID
		assertEquals(true, payment.isSignatureValid());
		
		//INVALID SIGNATURE
		payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp+1, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF PAYMENT SIGNATURE IS INVALID
		assertEquals(false, payment.isSignatureValid());
	}
	
	@Test
	public void validatePaymentTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		Account recipient = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		long timestamp = NTP.getTime();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE VALID PAYMENT
		Transaction payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);

		//CHECK IF PAYMENT IS VALID
		assertEquals(Transaction.VALIDATE_OKE, payment.isValid(databaseSet));
		
		//CREATE INVALID PAYMENT INVALID RECIPIENT ADDRESS
		payment = new PaymentTransaction(sender, new Account("test"), BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
	
		//CHECK IF PAYMENT IS INVALID
		assertNotEquals(Transaction.VALIDATE_OKE, payment.isValid(databaseSet));
		
		//CREATE INVALID PAYMENT NEGATIVE AMOUNT
		payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(-100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF PAYMENT IS INVALID
		assertNotEquals(Transaction.VALIDATE_OKE, payment.isValid(databaseSet));	
		
		//CREATE INVALID PAYMENT NEGATIVE FEE
		payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(-1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF PAYMENT IS INVALID
		assertNotEquals(Transaction.VALIDATE_OKE, payment.isValid(databaseSet));	
		
		//CREATE INVALID PAYMENT WRONG REFERENCE
		payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, new byte[0], signature);
						
		//CHECK IF PAYMENT IS INVALID
		assertNotEquals(Transaction.VALIDATE_OKE, payment.isValid(databaseSet));	
	}
	
	@Test
	public void parsePaymentTransaction() 
	{
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
						
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		Account recipient = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		long timestamp = NTP.getTime();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE VALID PAYMENT
		Transaction payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CONVERT TO BYTES
		byte[] rawPayment = payment.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			PaymentTransaction parsedPayment = (PaymentTransaction) TransactionFactory.getInstance().parse(rawPayment);
			
			//CHECK INSTANCE
			assertEquals(true, parsedPayment instanceof PaymentTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(payment.getSignature(), parsedPayment.getSignature()));
			
			//CHECK AMOUNT SENDER
			assertEquals(payment.getAmount(sender), parsedPayment.getAmount(sender));	
			
			//CHECK AMOUNT RECIPIENT
			assertEquals(payment.getAmount(recipient), parsedPayment.getAmount(recipient));	
			
			//CHECK FEE
			assertEquals(payment.getFee(), parsedPayment.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(payment.getReference(), parsedPayment.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(payment.getTimestamp(), parsedPayment.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawPayment = new byte[payment.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawPayment);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}
	
	@Test
	public void processPaymentTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
					
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
			
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
			
		//CREATE SIGNATURE
		Account recipient = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		long timestamp = NTP.getTime();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
			
		//CREATE PAYMENT
		Transaction payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		payment.process(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(899).setScale(8), sender.getConfirmedBalance(databaseSet));
				
		//CHECK BALANCE RECIPIENT
		assertEquals(BigDecimal.valueOf(100).setScale(8), recipient.getConfirmedBalance(databaseSet));
		
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(payment.getSignature(), sender.getLastReference(databaseSet)));
		
		//CHECK REFERENCE RECIPIENT
		assertEquals(true, Arrays.equals(payment.getSignature(), recipient.getLastReference(databaseSet)));
		
		//CREATE SIGNATURE
		signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
		
		//CREATE PAYMENT
		payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		payment.process(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(798).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK BALANCE RECIPIENT
		assertEquals(BigDecimal.valueOf(200).setScale(8), recipient.getConfirmedBalance(databaseSet));
				
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(payment.getSignature(), sender.getLastReference(databaseSet)));
					
		//CHECK REFERENCE RECIPIENT NOT CHANGED
		assertEquals(true, Arrays.equals(payment.getReference(), recipient.getLastReference(databaseSet)));
	}
	
	@Test
	public void orphanPaymentTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
					
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
			
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
			
		//CREATE SIGNATURE
		Account recipient = new Account("XUi2oga2pnGNcZ9es6pBqxydtRZKWdkL2g");
		long timestamp = NTP.getTime();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
			
		//CREATE PAYMENT
		Transaction payment = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		payment.process(databaseSet);
		
		//CREATE PAYMENT2
		signature = PaymentTransaction.generateSignature(databaseSet, sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp);
		Transaction payment2  = new PaymentTransaction(sender, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		payment.process(databaseSet);
		
		//ORPHAN PAYMENT
		payment2.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(899).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK BALANCE RECIPIENT
		assertEquals(BigDecimal.valueOf(100).setScale(8), recipient.getConfirmedBalance(databaseSet));
				
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(payment.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK REFERENCE RECIPIENT
		assertEquals(true, Arrays.equals(payment.getSignature(), recipient.getLastReference(databaseSet)));

		//ORPHAN PAYMENT
		payment.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(1000).setScale(8), sender.getConfirmedBalance(databaseSet));
								
		//CHECK BALANCE RECIPIENT
		assertEquals(BigDecimal.valueOf(0).setScale(8), recipient.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(transaction.getSignature(), sender.getLastReference(databaseSet)));
						
		//CHECK REFERENCE RECIPIENT
		assertEquals(true, Arrays.equals(new byte[0], recipient.getLastReference(databaseSet)));
	}

	//REGISTER NAME
	
	@Test
	public void validateSignatureRegisterNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE NAME
		Name name = new Name(sender, "test", "this is the value");
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
		
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(true, nameRegistration.isSignatureValid());
		
		//INVALID SIGNATURE
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(false, nameRegistration.isSignatureValid());
	}
	
	@Test
	public void validateRegisterNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameRegistration.isValid(databaseSet));
		nameRegistration.process(databaseSet);
		
		//CREATE INVALID NAME REGISTRATION INVALID NAME LENGTH
		String longName = "";
		for(int i=1; i<1000; i++)
		{
			longName += "oke";
		}
		name = new Name(sender, longName, "this is the value");
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_NAME_LENGTH, nameRegistration.isValid(databaseSet));
		
		//CREATE INVALID NAME REGISTRATION INVALID NAME LENGTH
		String longValue = "";
		for(int i=1; i<10000; i++)
		{
			longValue += "oke";
		}
		name = new Name(sender, "test2", longValue);
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_VALUE_LENGTH, nameRegistration.isValid(databaseSet));
		
		//CREATE INVALID NAME REGISTRATION NAME ALREADY TAKEN
		name = new Name(sender, "test", "this is the value");
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NAME_ALREADY_REGISTRED, nameRegistration.isValid(databaseSet));
		
		//CREATE INVALID NAME NOT ENOUGH BALANCE
		seed = Crypto.getInstance().digest("invalid".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount invalidOwner = new PrivateKeyAccount(privateKey);
		name = new Name(invalidOwner, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(invalidOwner, name, BigDecimal.ONE.setScale(8), timestamp, invalidOwner.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NO_BALANCE, nameRegistration.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID REFERENCE
		name = new Name(sender, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, invalidOwner.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_REFERENCE, nameRegistration.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID FEE
		name = new Name(sender, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ZERO.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NEGATIVE_FEE, nameRegistration.isValid(databaseSet));
	}

	@Test
	public void parseRegisterNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		RegisterNameTransaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CONVERT TO BYTES
		byte[] rawNameRegistration = nameRegistration.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			RegisterNameTransaction parsedRegistration = (RegisterNameTransaction) TransactionFactory.getInstance().parse(rawNameRegistration);
			
			//CHECK INSTANCE
			assertEquals(true, parsedRegistration instanceof RegisterNameTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(nameRegistration.getSignature(), parsedRegistration.getSignature()));
			
			//CHECK AMOUNT CREATOR
			assertEquals(nameRegistration.getAmount(sender), parsedRegistration.getAmount(sender));	
			
			//CHECK NAME OWNER
			assertEquals(nameRegistration.getName().getOwner().getAddress(), parsedRegistration.getName().getOwner().getAddress());	
			
			//CHECK NAME NAME
			assertEquals(nameRegistration.getName().getName(), parsedRegistration.getName().getName());	
			
			//CHECK NAME VALUE
			assertEquals(nameRegistration.getName().getValue(), parsedRegistration.getName().getValue());	
			
			//CHECK FEE
			assertEquals(nameRegistration.getFee(), parsedRegistration.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(nameRegistration.getReference(), parsedRegistration.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(nameRegistration.getTimestamp(), parsedRegistration.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawNameRegistration = new byte[nameRegistration.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawNameRegistration);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}

	@Test
	public void processRegisterNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(999).setScale(8), sender.getConfirmedBalance(databaseSet));
				
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameRegistration.getSignature(), sender.getLastReference(databaseSet)));
		
		//CHECK NAME EXISTS
		assertEquals(true, databaseSet.getNameDatabase().containsName(name));
	}
	
	@Test
	public void orphanRegisterNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		nameRegistration.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(1000).setScale(8), sender.getConfirmedBalance(databaseSet));
				
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(transaction.getSignature(), sender.getLastReference(databaseSet)));
		
		//CHECK NAME EXISTS
		assertEquals(false, databaseSet.getNameDatabase().containsName(name));
	}

	//UPDATE NAME
	
	@Test
	public void validateSignatureUpdateNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE NAME
		Name name = new Name(sender, "test", "this is the value");
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		byte[] signature = UpdateNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.ONE.setScale(8), timestamp);
		
		//CREATE NAME UPDATE
		Transaction nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(true, nameUpdate.isSignatureValid());
		
		//INVALID SIGNATURE
		nameUpdate = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(false, nameUpdate.isSignatureValid());

	}
	
	@Test
	public void validateUpdateNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameRegistration.isValid(databaseSet));
		nameRegistration.process(databaseSet);
		
		//CREATE NAME UPDATE
		name.setValue("new value");
		Transaction nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
	
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameUpdate.isValid(databaseSet));
		
		//CREATE INVALID NAME UPDATE INVALID NAME LENGTH
		String longName = "";
		for(int i=1; i<1000; i++)
		{
			longName += "oke";
		}
		name = new Name(sender, longName, "this is the value");
		nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.INVALID_NAME_LENGTH, nameUpdate.isValid(databaseSet));
		
		//CREATE INVALID NAME UPDATE NAME DOES NOT EXIST
		name = new Name(sender, "test2", "this is the value");
		nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NAME_DOES_NOT_EXIST, nameUpdate.isValid(databaseSet));
		
		//CREATE INVALID NAME UPDATE INCORRECT OWNER
		seed = Crypto.getInstance().digest("invalid".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount invalidOwner = new PrivateKeyAccount(privateKey);
		name = new Name(invalidOwner, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(invalidOwner, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		nameRegistration.process(databaseSet);	
		
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.INVALID_NAME_OWNER, nameUpdate.isValid(databaseSet));
				
		//CREATE INVALID NAME UPDATE NO BALANCE
		name = new Name(invalidOwner, "test2", "this is the value");
		nameUpdate = new UpdateNameTransaction(invalidOwner, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NO_BALANCE, nameUpdate.isValid(databaseSet));
				
		//CREATE NAME UPDATE INVALID REFERENCE
		name = new Name(sender, "test", "this is the value");
		nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, new byte[]{}, signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_REFERENCE, nameUpdate.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID FEE
		name = new Name(sender, "test", "this is the value");
		nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ZERO.setScale(8).subtract(BigDecimal.ONE), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NEGATIVE_FEE, nameUpdate.isValid(databaseSet));
	}

	@Test
	public void parseUpdateNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME UPDATE
		UpdateNameTransaction nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CONVERT TO BYTES
		byte[] rawNameUpdate = nameUpdate.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			UpdateNameTransaction parsedUpdate = (UpdateNameTransaction) TransactionFactory.getInstance().parse(rawNameUpdate);
			
			//CHECK INSTANCE
			assertEquals(true, parsedUpdate instanceof UpdateNameTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(nameUpdate.getSignature(), parsedUpdate.getSignature()));
			
			//CHECK AMOUNT CREATOR
			assertEquals(nameUpdate.getAmount(sender), parsedUpdate.getAmount(sender));	
			
			//CHECK OWNER
			assertEquals(nameUpdate.getOwner().getAddress(), parsedUpdate.getOwner().getAddress());	
			
			//CHECK NAME OWNER
			assertEquals(nameUpdate.getName().getOwner().getAddress(), parsedUpdate.getName().getOwner().getAddress());	
			
			//CHECK NAME NAME
			assertEquals(nameUpdate.getName().getName(), parsedUpdate.getName().getName());	
			
			//CHECK NAME VALUE
			assertEquals(nameUpdate.getName().getValue(), parsedUpdate.getName().getValue());	
			
			//CHECK FEE
			assertEquals(nameUpdate.getFee(), parsedUpdate.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(nameUpdate.getReference(), parsedUpdate.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(nameUpdate.getTimestamp(), parsedUpdate.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawNameUpdate = new byte[nameUpdate.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawNameUpdate);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}

	@Test
	public void processUpdateNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE NAME UPDATE
		name = new Name(new Account("XYLEQnuvhracK2WMN3Hjif67knkJe9hTQn"), "test", "new value");
		Transaction nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameUpdate.process(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(998).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameUpdate.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME EXISTS
		assertEquals(true, databaseSet.getNameDatabase().containsName(name));
		
		//CHECK NAME VALUE
		name =  databaseSet.getNameDatabase().getName("test");
		assertEquals("new value", name.getValue());
		
		//CHECK NAME OWNER
		assertEquals("XYLEQnuvhracK2WMN3Hjif67knkJe9hTQn", name.getOwner().getAddress());
	}

	
	@Test
	public void orphanUpdateNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE NAME UPDATE
		name = new Name(new Account("XYLEQnuvhracK2WMN3Hjif67knkJe9hTQn"), "test", "new value");
		Transaction nameUpdate = new UpdateNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameUpdate.process(databaseSet);
		nameUpdate.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(999).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameRegistration.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME EXISTS
		assertEquals(true, databaseSet.getNameDatabase().containsName(name));
		
		//CHECK NAME VALUE
		name =  databaseSet.getNameDatabase().getName("test");
		assertEquals("new value", name.getValue());
		
		//CHECK NAME OWNER
		assertEquals(sender.getAddress(), name.getOwner().getAddress());
	}
	
	//SELL NAME
	
	@Test
	public void validateSignatureSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE NAME
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		byte[] signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.ONE.setScale(8), timestamp);
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(true, nameSaleTransaction.isSignatureValid());
		
		//INVALID SIGNATURE
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(false, nameSaleTransaction.isSignatureValid());
	}
	
	@Test
	public void validateSellNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameRegistration.isValid(databaseSet));
		nameRegistration.process(databaseSet);
		
		//CREATE NAME SALE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
	
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME SALE INVALID NAME LENGTH
		String longName = "";
		for(int i=1; i<1000; i++)
		{
			longName += "oke";
		}
		nameSale = new NameSale(longName, BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.INVALID_NAME_LENGTH, nameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME SALE NAME DOES NOT EXIST
		nameSale = new NameSale("test2", BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NAME_DOES_NOT_EXIST, nameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME UPDATE INCORRECT OWNER
		seed = Crypto.getInstance().digest("invalid".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount invalidOwner = new PrivateKeyAccount(privateKey);
		name = new Name(invalidOwner, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(invalidOwner, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		nameRegistration.process(databaseSet);	
		
		//CHECK IF NAME UPDATE IS INVALID
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		assertEquals(Transaction.INVALID_NAME_OWNER, nameSaleTransaction.isValid(databaseSet));
				
		//CREATE INVALID NAME UPDATE NO BALANCE
		nameSale = new NameSale("test2", BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(invalidOwner, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NO_BALANCE, nameSaleTransaction.isValid(databaseSet));
				
		//CREATE NAME UPDATE INVALID REFERENCE
		nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, new byte[]{}, signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_REFERENCE, nameSaleTransaction.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID FEE
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ZERO.setScale(8).subtract(BigDecimal.ONE), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NEGATIVE_FEE, nameSaleTransaction.isValid(databaseSet));
		
		//CREATE NAME UPDATE PROCESS 
		nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		nameSaleTransaction.process(databaseSet);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NAME_ALREADY_FOR_SALE, nameSaleTransaction.isValid(databaseSet));
	}

	@Test
	public void parseSellNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		NameSale nameSale = new NameSale("test", BigDecimal.valueOf(1).setScale(8));
		byte[] signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME UPDATE
		SellNameTransaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CONVERT TO BYTES
		byte[] rawNameSale = nameSaleTransaction.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			SellNameTransaction parsedNameSale = (SellNameTransaction) TransactionFactory.getInstance().parse(rawNameSale);
			
			//CHECK INSTANCE
			assertEquals(true, parsedNameSale instanceof SellNameTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(nameSaleTransaction.getSignature(), parsedNameSale.getSignature()));
			
			//CHECK AMOUNT CREATOR
			assertEquals(nameSaleTransaction.getAmount(sender), parsedNameSale.getAmount(sender));	
			
			//CHECK OWNER
			assertEquals(nameSaleTransaction.getOwner().getAddress(), parsedNameSale.getOwner().getAddress());	
			
			//CHECK NAMESALE NAME
			assertEquals(nameSaleTransaction.getNameSale().getKey(), parsedNameSale.getNameSale().getKey());	
			
			//CHECK NAMESALE AMOUNT
			assertEquals(nameSaleTransaction.getNameSale().getAmount(), parsedNameSale.getNameSale().getAmount());	
			
			//CHECK FEE
			assertEquals(nameSaleTransaction.getFee(), parsedNameSale.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(nameSaleTransaction.getReference(), parsedNameSale.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(nameSaleTransaction.getTimestamp(), parsedNameSale.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawNameSale = new byte[nameSaleTransaction.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawNameSale);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}

	@Test
	public void processSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(998).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameSaleTransaction.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME SALE EXISTS
		assertEquals(true, databaseSet.getNameExchangeDatabase().containsName("test"));
		
		//CHECK NAME SALE AMOUNT
		nameSale =  databaseSet.getNameExchangeDatabase().getNameSale("test");
		assertEquals(BigDecimal.ONE.setScale(8), nameSale.getAmount());
	}

	@Test
	public void orphanSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
						
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		nameSaleTransaction.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(999).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameRegistration.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME SALE EXISTS
		assertEquals(false, databaseSet.getNameExchangeDatabase().containsName("test"));
	}
	
	
	//CANCEL SELL NAME
	
	@Test
	public void validateSignatureCancelSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		byte[] signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.ONE.setScale(8), timestamp);
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(true, nameSaleTransaction.isSignatureValid());
		
		//INVALID SIGNATURE
		nameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(false, nameSaleTransaction.isSignatureValid());
	}
	
	@Test
	public void validateCancelSellNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameRegistration.isValid(databaseSet));
		nameRegistration.process(databaseSet);
		
		//CREATE NAME SALE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
	
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameSaleTransaction.isValid(databaseSet));
		nameSaleTransaction.process(databaseSet);
		
		//CREATE CANCEL NAME SALE
		CancelSellNameTransaction cancelNameSaleTransaction = new CancelSellNameTransaction(sender, nameSale.getKey(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF CANCEL NAME UPDATE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, cancelNameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID CANCEL NAME SALE INVALID NAME LENGTH
		String longName = "";
		for(int i=1; i<1000; i++)
		{
			longName += "oke";
		}
		
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, longName, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		

		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.INVALID_NAME_LENGTH, cancelNameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID CANCEL NAME SALE NAME DOES NOT EXIST
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test2", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NAME_DOES_NOT_EXIST, cancelNameSaleTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME UPDATE INCORRECT OWNER
		seed = Crypto.getInstance().digest("invalid".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount invalidOwner = new PrivateKeyAccount(privateKey);
		name = new Name(invalidOwner, "test2", "this is the value");
		nameRegistration = new RegisterNameTransaction(invalidOwner, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		nameRegistration.process(databaseSet);	
		
		//CREATE NAME SALE
		nameSale = new NameSale("test2", BigDecimal.ONE.setScale(8));
		nameSaleTransaction = new SellNameTransaction(invalidOwner, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		nameSaleTransaction.process(databaseSet);	
		
		//CHECK IF NAME UPDATE IS INVALID
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test2", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		assertEquals(Transaction.INVALID_NAME_OWNER, cancelNameSaleTransaction.isValid(databaseSet));
				
		//CREATE INVALID NAME UPDATE NO BALANCE
		cancelNameSaleTransaction = new CancelSellNameTransaction(invalidOwner, "test2", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NO_BALANCE, cancelNameSaleTransaction.isValid(databaseSet));
				
		//CREATE NAME UPDATE INVALID REFERENCE
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, new byte[]{}, signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_REFERENCE, cancelNameSaleTransaction.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID FEE
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ZERO.setScale(8).subtract(BigDecimal.ONE), timestamp, sender.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NEGATIVE_FEE, cancelNameSaleTransaction.isValid(databaseSet));
		
		//CREATE NAME UPDATE PROCESS 
		cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		cancelNameSaleTransaction.process(databaseSet);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NAME_NOT_FOR_SALE, cancelNameSaleTransaction.isValid(databaseSet));
	}

	@Test
	public void parseCancelSellNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		byte[] signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE CANCEL NAME SALE
		CancelSellNameTransaction cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CONVERT TO BYTES
		byte[] rawCancelNameSale = cancelNameSaleTransaction.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			CancelSellNameTransaction parsedCancelNameSale = (CancelSellNameTransaction) TransactionFactory.getInstance().parse(rawCancelNameSale);
			
			//CHECK INSTANCE
			assertEquals(true, parsedCancelNameSale instanceof CancelSellNameTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(cancelNameSaleTransaction.getSignature(), parsedCancelNameSale.getSignature()));
			
			//CHECK AMOUNT CREATOR
			assertEquals(cancelNameSaleTransaction.getAmount(sender), parsedCancelNameSale.getAmount(sender));	
			
			//CHECK OWNER
			assertEquals(cancelNameSaleTransaction.getOwner().getAddress(), parsedCancelNameSale.getOwner().getAddress());	
			
			//CHECK NAME
			assertEquals(cancelNameSaleTransaction.getName(), parsedCancelNameSale.getName());	
			
			//CHECK FEE
			assertEquals(cancelNameSaleTransaction.getFee(), parsedCancelNameSale.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(cancelNameSaleTransaction.getReference(), parsedCancelNameSale.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(cancelNameSaleTransaction.getTimestamp(), parsedCancelNameSale.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawCancelNameSale = new byte[cancelNameSaleTransaction.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawCancelNameSale);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}
	
	@Test
	public void processCancelSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		
		//CREATE SIGNATURE
		signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.valueOf(1).setScale(8), timestamp);			
			
		//CREATE CANCEL NAME SALE
		Transaction cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		cancelNameSaleTransaction.process(databaseSet);	
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(997).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(cancelNameSaleTransaction.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME SALE EXISTS
		assertEquals(false, databaseSet.getNameExchangeDatabase().containsName("test"));
	}

	@Test
	public void orphanCancelSellNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
					
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		
		//CREATE SIGNATURE
		signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.valueOf(1).setScale(8), timestamp);			
			
		//CREATE CANCEL NAME SALE
		Transaction cancelNameSaleTransaction = new CancelSellNameTransaction(sender, "test", BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		cancelNameSaleTransaction.process(databaseSet);	
		cancelNameSaleTransaction.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(998).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE SENDER
		assertEquals(true, Arrays.equals(nameSaleTransaction.getSignature(), sender.getLastReference(databaseSet)));
				
		//CHECK NAME SALE EXISTS
		assertEquals(true, databaseSet.getNameExchangeDatabase().containsName("test"));
		
		//CHECK NAME SALE AMOUNT
		nameSale =  databaseSet.getNameExchangeDatabase().getNameSale("test");
		assertEquals(BigDecimal.ONE.setScale(8), nameSale.getAmount());
	}
	
	//BUY NAME
	
	@Test
	public void validateSignatureBuyNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
				
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		byte[] signature = BuyNameTransaction.generateSignature(databaseSet, sender, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp);
		
		//CREATE NAME SALE
		Transaction buyNameTransaction = new BuyNameTransaction(sender, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
		
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(true, buyNameTransaction.isSignatureValid());
		
		//INVALID SIGNATURE
		buyNameTransaction = new BuyNameTransaction(sender,nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), new byte[0]);
		
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(false, buyNameTransaction.isSignatureValid());
	}
	
	@Test
	public void validateBuyNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//CREATE KNOWN ACCOUNT
		seed = Crypto.getInstance().digest("buyer".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount buyer = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE BUYER HAS FUNDS
		transaction = new GenesisTransaction(buyer, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CHECK IF NAME REGISTRATION IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameRegistration.isValid(databaseSet));
		nameRegistration.process(databaseSet);
		
		//CREATE NAME SALE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);
	
		//CHECK IF NAME UPDATE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, nameSaleTransaction.isValid(databaseSet));
		nameSaleTransaction.process(databaseSet);
		
		//CREATE NAME PURCHASE
		BuyNameTransaction namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);		

		//CHECK IF NAME PURCHASE IS VALID
		assertEquals(Transaction.VALIDATE_OKE, namePurchaseTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME PURCHASE INVALID NAME LENGTH
		String longName = "";
		for(int i=1; i<1000; i++)
		{
			longName += "oke";
		}
		
		nameSale = new NameSale(longName, nameSale.getAmount());
		namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);		

		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.INVALID_NAME_LENGTH, namePurchaseTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME PURCHASE NAME DOES NOT EXIST
		nameSale = new NameSale("test2", BigDecimal.ONE.setScale(8));
		namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale,nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);		
		
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NAME_DOES_NOT_EXIST, namePurchaseTransaction.isValid(databaseSet));
		
		//CREATE INVALID NAME PURCHASE NAME NOT FOR SALE
		Name test2 = new Name(sender, "test2", "oke");
		databaseSet.getNameDatabase().addName(test2);
		
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NAME_NOT_FOR_SALE, namePurchaseTransaction.isValid(databaseSet));
						
		//CREATE INVALID NAME PURCHASE ALREADY OWNER
		nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		namePurchaseTransaction = new BuyNameTransaction(sender, nameSale,nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);		
		
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.BUYER_ALREADY_OWNER, namePurchaseTransaction.isValid(databaseSet));
				
		//CREATE INVALID NAME UPDATE NO BALANCE
		buyer.setConfirmedBalance(BigDecimal.ZERO.setScale(8), databaseSet);
		namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale,nameSale.getName(databaseSet).getOwner(),BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);		
		
		//CHECK IF NAME UPDATE IS INVALID
		assertEquals(Transaction.NO_BALANCE, namePurchaseTransaction.isValid(databaseSet));
		buyer.setConfirmedBalance(BigDecimal.valueOf(1000).setScale(8), databaseSet);
				
		//CREATE NAME UPDATE INVALID REFERENCE
		namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(),BigDecimal.ONE.setScale(8), timestamp, new byte[]{}, signature);		
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.INVALID_REFERENCE, namePurchaseTransaction.isValid(databaseSet));
		
		//CREATE NAME REGISTRATION INVALID FEE
		namePurchaseTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ZERO.setScale(8).subtract(BigDecimal.ONE), timestamp, buyer.getLastReference(databaseSet), signature);
				
		//CHECK IF NAME REGISTRATION IS INVALID
		assertEquals(Transaction.NEGATIVE_FEE, namePurchaseTransaction.isValid(databaseSet));
	}

	@Test
	public void parseBuyNameTransaction() 
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
						
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
				
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		NameSale nameSale = new NameSale("test", BigDecimal.valueOf(1).setScale(8));
		byte[] signature = BuyNameTransaction.generateSignature(databaseSet, sender, nameSale,nameSale.getName(databaseSet).getOwner(), BigDecimal.valueOf(1).setScale(8), timestamp);
				
		//CREATE CANCEL NAME SALE
		BuyNameTransaction namePurchaseTransaction = new BuyNameTransaction(sender, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);	
		
		//CONVERT TO BYTES
		byte[] rawNamePurchase = namePurchaseTransaction.toBytes();
		
		try 
		{	
			//PARSE FROM BYTES
			BuyNameTransaction parsedNamePurchase = (BuyNameTransaction) TransactionFactory.getInstance().parse(rawNamePurchase);
			
			//CHECK INSTANCE
			assertEquals(true, parsedNamePurchase instanceof BuyNameTransaction);
			
			//CHECK SIGNATURE
			assertEquals(true, Arrays.equals(namePurchaseTransaction.getSignature(), parsedNamePurchase.getSignature()));
			
			//CHECK AMOUNT BUYER
			assertEquals(namePurchaseTransaction.getAmount(sender), parsedNamePurchase.getAmount(sender));	
			
			//CHECK OWNER
			assertEquals(namePurchaseTransaction.getBuyer().getAddress(), parsedNamePurchase.getBuyer().getAddress());	
			
			//CHECK NAME
			assertEquals(namePurchaseTransaction.getNameSale().getKey(), parsedNamePurchase.getNameSale().getKey());	
		
			//CHECK FEE
			assertEquals(namePurchaseTransaction.getFee(), parsedNamePurchase.getFee());	
			
			//CHECK REFERENCE
			assertEquals(true, Arrays.equals(namePurchaseTransaction.getReference(), parsedNamePurchase.getReference()));	
			
			//CHECK TIMESTAMP
			assertEquals(namePurchaseTransaction.getTimestamp(), parsedNamePurchase.getTimestamp());				
		}
		catch (Exception e) 
		{
			fail("Exception while parsing transaction.");
		}
		
		//PARSE TRANSACTION FROM WRONG BYTES
		rawNamePurchase = new byte[namePurchaseTransaction.getDataLength()];
		
		try 
		{	
			//PARSE FROM BYTES
			TransactionFactory.getInstance().parse(rawNamePurchase);
			
			//FAIL
			fail("this should throw an exception");
		}
		catch (Exception e) 
		{
			//EXCEPTION IS THROWN OKE
		}	
	}
	
	@Test
	public void processBuyNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//CREATE KNOWN ACCOUNT
		seed = Crypto.getInstance().digest("buyer".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount buyer = new PrivateKeyAccount(privateKey);		
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE BUYER HAS FUNDS
		transaction = new GenesisTransaction(buyer, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);			
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		
		//CREATE SIGNATURE
		signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.valueOf(1).setScale(8), timestamp);			
			
		//CREATE NAME PURCHASE
		Transaction purchaseNameTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);			
		purchaseNameTransaction.process(databaseSet);	
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(998).setScale(8), buyer.getConfirmedBalance(databaseSet));
		
		//CHECK BALANCE SELLER
		assertEquals(BigDecimal.valueOf(999).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE BUYER
		assertEquals(true, Arrays.equals(purchaseNameTransaction.getSignature(), buyer.getLastReference(databaseSet)));
				
		//CHECK NAME OWNER
		name = databaseSet.getNameDatabase().getName("test");
		assertEquals(name.getOwner().getAddress(), buyer.getAddress());
	
		//CHECK NAME SALE EXISTS
		assertEquals(false, databaseSet.getNameExchangeDatabase().containsName("test"));
	}

	@Test
	public void orphanBuyNameTransaction()
	{
		Ed25519.load();
		
		//CREATE EMPTY MEMORY DATABASE
		DatabaseSet databaseSet = DatabaseSet.createEmptyDatabaseSet();
								
		//CREATE KNOWN ACCOUNT
		byte[] seed = Crypto.getInstance().digest("test".getBytes());
		byte[] privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount sender = new PrivateKeyAccount(privateKey);
		
		//CREATE KNOWN ACCOUNT
		seed = Crypto.getInstance().digest("buyer".getBytes());
		privateKey = Crypto.getInstance().createKeyPair(seed).getA();
		PrivateKeyAccount buyer = new PrivateKeyAccount(privateKey);		
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE SENDER HAS FUNDS
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);
		
		//PROCESS GENESIS TRANSACTION TO MAKE SURE BUYER HAS FUNDS
		transaction = new GenesisTransaction(buyer, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);			
				
		//CREATE SIGNATURE
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "test", "this is the value");
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);
						
		//CREATE NAME REGISTRATION
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameRegistration.process(databaseSet);
		
		//CREATE SIGNATURE
		NameSale nameSale = new NameSale("test", BigDecimal.ONE.setScale(8));
		signature = SellNameTransaction.generateSignature(databaseSet, sender, nameSale, BigDecimal.valueOf(1).setScale(8), timestamp);			
		
		//CREATE NAME SALE
		Transaction nameSaleTransaction = new SellNameTransaction(sender, nameSale, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), signature);			
		nameSaleTransaction.process(databaseSet);
		
		//CREATE SIGNATURE
		signature = CancelSellNameTransaction.generateSignature(databaseSet, sender, "test", BigDecimal.valueOf(1).setScale(8), timestamp);			
			
		//CREATE NAME PURCHASE
		Transaction purchaseNameTransaction = new BuyNameTransaction(buyer, nameSale, nameSale.getName(databaseSet).getOwner(), BigDecimal.ONE.setScale(8), timestamp, buyer.getLastReference(databaseSet), signature);			
		purchaseNameTransaction.process(databaseSet);	
		purchaseNameTransaction.orphan(databaseSet);
		
		//CHECK BALANCE SENDER
		assertEquals(BigDecimal.valueOf(1000).setScale(8), buyer.getConfirmedBalance(databaseSet));
		
		//CHECK BALANCE SELLER
		assertEquals(BigDecimal.valueOf(998).setScale(8), sender.getConfirmedBalance(databaseSet));
						
		//CHECK REFERENCE BUYER
		assertEquals(true, Arrays.equals(transaction.getSignature(), buyer.getLastReference(databaseSet)));
				
		//CHECK NAME OWNER
		name = databaseSet.getNameDatabase().getName("test");
		assertEquals(name.getOwner().getAddress(), sender.getAddress());
	
		//CHECK NAME SALE EXISTS
		assertEquals(true, databaseSet.getNameExchangeDatabase().containsName("test"));
	}
	
		
}