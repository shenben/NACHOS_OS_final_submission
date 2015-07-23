package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		// System.out.println("\n ***Testing Boats with only 2 children***");
		// begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		begin(3, 3, b);

		// System.out.println("\n ***Testing Boats with 20 children, 10 adults***");
		// begin(10, 20, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		Lib.assertTrue(children >= 2);
		Lib.assertTrue(b != null);

		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		adultsOnOahu = adults;
		childrenOnOahu = children;
		boatState = boatEmpty;
		boatOnOahu = true;
		commonLock = new Lock();
		sleepAdultsOahu = new Condition2(commonLock);
		sleepAdultsMolokai = new Condition2(commonLock);
		sleepChildrenOahu = new Condition2(commonLock);
		sleepChildrenMolokai = new Condition2(commonLock);
		finished = new Semaphore(0);

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		for (int i = 0; i < adults; i++) {
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Adult " + i + "Thread");
			t.fork();
		}

		for (int i = 0; i < children; i++) {
			Runnable r = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Child " + i + "Thread");
			t.fork();
		}

		finished.P();

		System.out.println(" ***Boat Test ends!***");
	}

	static void AdultItinerary() {
		boolean onOahu = true;		//adults never go back to oahu
		commonLock.acquire();
		while (true) {
			Lib.assertTrue(onOahu);
					//if there is 1 or less children at oahu, then there are one or more children at molokai
			if (boatState == boatEmpty && boatOnOahu && childrenOnOahu <= 1) 
			{
				onOahu = false;
				adultsOnOahu--;					
				boatOnOahu = false;
				bg.AdultRowToMolokai();							//adult leaves oahu
				if (adultsOnOahu == 0 && childrenOnOahu == 0) 
				{
					finished.V();						//add +1 to "done list"
					sleepAdultsMolokai.sleep();			//release lock and sleep
				}
				sleepChildrenMolokai.wakeAll();			//remove all from condition2.waitqueue
				sleepAdultsMolokai.sleep();				//release lock and sleep
			}
			else	//cant get on boat, boat is not at oahu, no children waiting on other side
				sleepAdultsOahu.sleep();	
		}
	}

	static void ChildItinerary() {
		boolean onOahu = true;	//children can go back and forth
		commonLock.acquire();
		while (true)
			if (onOahu) 
			{
				if (boatOnOahu && boatState == boatEmpty) 
				{
					onOahu = false;
					childrenOnOahu--;
					bg.ChildRowToMolokai();		//leave oahu
					if (childrenOnOahu > 0) 	//if more children can get on the boat...
					{
						boatState = boatHalf;
						sleepChildrenOahu.wakeAll();
					}
					else //there are no more children at oahu. loner rower kid
					{
						boatOnOahu = false;
						boatState = boatEmpty;
						if (adultsOnOahu == 0 && childrenOnOahu == 0) 
						{
							finished.V();								//child done
							sleepChildrenMolokai.sleep();			//release lock and sleep
						}
						sleepChildrenMolokai.wakeAll();
					}
					sleepChildrenMolokai.sleep();					//release lock and sleep
				}
				else if (boatOnOahu && boatState == boatHalf) 
				{
					onOahu = false;
					childrenOnOahu--;
					bg.ChildRideToMolokai();	//current child rows to molokai
					boatOnOahu = false;			//boat crosses
					boatState = boatEmpty;		//children stay at molokai
					if (adultsOnOahu == 0 && childrenOnOahu == 0) 
					{
						finished.V();									
						sleepChildrenMolokai.sleep();
					}
					sleepChildrenMolokai.wakeAll();					//remove all from condition2.waitqueue
					sleepChildrenMolokai.sleep();					//release lock and sleep
				}
				else	//boatonOahu && boatfull
					sleepChildrenOahu.sleep();
			}//if on oahu
			else 	//else on molokai
			{
				if (!boatOnOahu) 					//boat is on molokai
				{
					bg.ChildRowToOahu();			//go back to oahu for adult or child
					childrenOnOahu++;
					boatOnOahu = true;
					sleepAdultsOahu.wakeAll();
					sleepChildrenOahu.wakeAll();	
					onOahu = true;					
					sleepChildrenOahu.sleep();		//release lock and sleep
				}
				else								//boat is not here
					sleepChildrenMolokai.sleep();	//release lock and sleep
			}
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

	static int adultsOnOahu, childrenOnOahu;
	static boolean boatOnOahu;
	static int boatState;
	static final int boatEmpty = 0, boatHalf = 1, boatFull = 2;
	static Lock commonLock;
	static Condition2 sleepAdultsOahu, sleepAdultsMolokai;
	static Condition2 sleepChildrenOahu, sleepChildrenMolokai;
	static Semaphore finished;
}