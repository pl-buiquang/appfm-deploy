/**
 * Created by buiquang on 9/15/15.
 */

import collection.mutable.Stack
import org.scalatest._

class ModuleManagerTests extends FlatSpec with Matchers {

  "The module manager" should "do something" in {
    val stack = new Stack[Int]
    stack.push(1)
    stack.push(2)
    stack.pop() should be (2)
    stack.pop() should be (1)
  }

  it should "throw NoSuchElementException if an empty stack is popped" in {
    val emptyStack = new Stack[Int]
    a [NoSuchElementException] should be thrownBy {
      emptyStack.pop()
    }
  }

}
